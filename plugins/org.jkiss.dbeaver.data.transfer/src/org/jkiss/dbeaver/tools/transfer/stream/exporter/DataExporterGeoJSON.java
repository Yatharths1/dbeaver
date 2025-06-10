package org.jkiss.dbeaver.tools.transfer.stream.exporter;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.exec.DBCResultSet;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.tools.transfer.stream.IDocumentDataExporter;
import org.jkiss.dbeaver.tools.transfer.stream.IStreamDataExporterSite;
import org.jkiss.utils.CommonUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

public class DataExporterGeoJSON extends StreamExporterAbstract implements IDocumentDataExporter {

    private DBDAttributeBinding[] columns;
    private int rowNum = 0;
    private int latitudeIndex = -1;
    private int longitudeIndex = -1;
    private int wkbIndex = -1;

    @Override
    public void init(IStreamDataExporterSite site) throws DBException {
        super.init(site);
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public void exportHeader(DBCSession session) throws DBException, IOException {
        columns = getSite().getAttributes();

        for (int i = 0; i < columns.length; i++) {
            String name = columns[i].getName().toLowerCase(Locale.ROOT);
            if (name.contains("lat")) {
                latitudeIndex = i;
            } else if (name.contains("lon") || name.contains("lng")) {
                longitudeIndex = i;
            } else if (name.equals("wkb_geometry")) {
                wkbIndex = i;
            }
        }

        if (latitudeIndex == -1 || longitudeIndex == -1 || wkbIndex == -1) {
            throw new DBException("Required columns (Latitude, Longitude, wkb_geometry) not found.");
        }

        PrintWriter out = getWriter();
        out.write("{\n");
        out.write("  \"type\": \"FeatureCollection\",\n");
        out.write("  \"features\": [\n");
        rowNum = 0;
    }

    @Override
    public void exportRow(DBCSession session, DBCResultSet resultSet, Object[] row) throws DBException, IOException {
        PrintWriter out = getWriter();
        if (rowNum > 0) {
            out.write(",\n");
        }

        Object lat = row[latitudeIndex];
        Object lon = row[longitudeIndex];
        Object wkbGeometry = row[wkbIndex];

        if (wkbGeometry == null) {
            throw new DBException("wkb_geometry value is null.");
        }

        String geometryType = detectWKTType(wkbGeometry.toString());
        String geometryCoordinates = parseWKTGeometry(wkbGeometry.toString());

        out.write("    {\n");
        out.write("      \"type\": \"Feature\",\n");
        out.write("      \"geometry\": {\n");
        out.write("        \"type\": \"" + geometryType + "\",\n");
        out.write("        \"coordinates\": " + geometryCoordinates + "\n");
        out.write("      },\n");
        out.write("      \"properties\": {\n");

        boolean firstProp = true;
        for (int i = 0; i < columns.length; i++) {
            if (i == wkbIndex) continue;

            if (!firstProp) {
                out.write(",\n");
            }
            firstProp = false;

            String columnName = columns[i].getName();
            Object cellValue = row[i];

            out.write("        \"" + columnName + "\": ");
            if (cellValue == null) {
                out.write("null");
            } else if (CommonUtils.isNumber(cellValue)) {
                out.write(cellValue.toString());
            } else {
                out.write("\"" + escapeJson(cellValue.toString()) + "\"");
            }
        }

        out.write("\n      }\n");
        out.write("    }");

        rowNum++;
    }

    @Override
    public void exportFooter(DBRProgressMonitor monitor) throws IOException {
        PrintWriter out = getWriter();
        out.write("\n  ]\n");
        out.write("}\n");
    }

    private String escapeJson(String value) {
        return value.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String detectWKTType(String wkt) {
        String trimmed = wkt.trim().toUpperCase();
        if (trimmed.startsWith("MULTIPOLYGON")) return "MultiPolygon";
        if (trimmed.startsWith("POLYGON")) return "Polygon";
        return "Geometry";
    }

    private String parseWKTGeometry(String wkt) {
        String upper = wkt.trim().toUpperCase();
        if (upper.startsWith("MULTIPOLYGON")) {
            return parseWKTMultiPolygon(wkt);
        } else if (upper.startsWith("POLYGON")) {
            return parseWKTPolygon(wkt);
        } else {
            return "[]";
        }
    }

    private String parseWKTPolygon(String wkt) {
        int start = wkt.indexOf("((");
        int end = wkt.lastIndexOf("))");
        if (start == -1 || end == -1 || start >= end) {
            return "[]";
        }

        String coordBody = wkt.substring(start + 2, end);
        String[] rings = coordBody.split("\\)\\s*,\\s*\\(");

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int r = 0; r < rings.length; r++) {
            if (r > 0) sb.append(",");

            sb.append("[");
            String[] points = rings[r].split(",");

            for (int p = 0; p < points.length; p++) {
                if (p > 0) sb.append(",");
                String[] coords = points[p].trim().split("\\s+");
                if (coords.length == 2) {
                    sb.append("[").append(coords[0]).append(",").append(coords[1]).append("]");
                }
            }
            sb.append("]");
        }

        sb.append("]");
        return sb.toString();
    }

    private String parseWKTMultiPolygon(String wkt) {
        int start = wkt.indexOf("(((");
        int end = wkt.lastIndexOf(")))");
        if (start == -1 || end == -1 || start >= end) {
            return "[]";
        }

        String content = wkt.substring(start + 3, end);
        String[] polygons = content.split("\\)\\s*\\)\\s*,\\s*\\(\\(");

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < polygons.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("[");

            String[] rings = polygons[i].split("\\)\\s*,\\s*\\(");
            for (int j = 0; j < rings.length; j++) {
                if (j > 0) sb.append(",");
                sb.append("[");

                String[] points = rings[j].split(",");
                for (int k = 0; k < points.length; k++) {
                    if (k > 0) sb.append(",");
                    String[] coords = points[k].trim().split("\\s+");
                    if (coords.length == 2) {
                        sb.append("[").append(coords[0]).append(",").append(coords[1]).append("]");
                    }
                }

                sb.append("]");
            }

            sb.append("]");
        }

        sb.append("]");
        return sb.toString();
    }
}