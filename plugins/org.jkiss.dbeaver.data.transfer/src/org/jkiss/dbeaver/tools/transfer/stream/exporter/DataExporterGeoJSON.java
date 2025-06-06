package org.jkiss.dbeaver.tools.transfer.stream.exporter;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.exec.DBCResultSet;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.tools.transfer.stream.IDocumentDataExporter;
import org.jkiss.dbeaver.tools.transfer.stream.IStreamDataExporterSite;
import org.jkiss.dbeaver.utils.ContentUtils;
import org.jkiss.dbeaver.utils.MimeTypes;
import org.jkiss.utils.CommonUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

public class DataExporterGeoJSON extends StreamExporterAbstract implements IDocumentDataExporter {

    private DBDAttributeBinding[] columns;
    private int rowNum = 0;
    private int latitudeIndex = -1;
    private int longitudeIndex = -1;

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
            }
        }

        if (latitudeIndex == -1 || longitudeIndex == -1) {
            throw new DBException("Latitude and Longitude columns not found.");
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
        if (lat == null || lon == null) return;

        out.write("    {\n");
        out.write("      \"type\": \"Feature\",\n");
        out.write("      \"geometry\": {\n");
        out.write("        \"type\": \"Point\",\n");
        out.write("        \"coordinates\": [" + lon + ", " + lat + "]\n");
        out.write("      },\n");
        out.write("      \"properties\": {\n");

        boolean firstProp = true;
        for (int i = 0; i < columns.length; i++) {
            if (i == latitudeIndex || i == longitudeIndex) continue;

            if (!firstProp) {
                out.write(",\n");
            }
            firstProp = false;

            String columnName = columns[i].getName();
            Object cellValue = row[i];
            out.write("        \"" + columnName + "\": " +
                (cellValue == null ? "null" : "\"" + escapeJson(cellValue.toString()) + "\""));
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
}