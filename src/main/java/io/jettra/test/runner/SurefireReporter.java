package io.jettra.test.runner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SurefireReporter {

    public static void writeReport(String targetDir, String className, int tests, int failures, int errors, int skipped, double time, String failureDetails) {
        File reportsDir = new File(targetDir, "surefire-reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        File reportFile = new File(reportsDir, "TEST-" + className + ".xml");
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write(String.format(java.util.Locale.US, "<testsuite name=\"%s\" time=\"%.3f\" tests=\"%d\" errors=\"%d\" skipped=\"%d\" failures=\"%d\">\n", 
                    className, time, tests, errors, skipped, failures));

            // Para simplicidad, solo agregamos los fallos como detalle general de la suite o en el tag principal
            if (failures > 0 || errors > 0) {
                writer.write("  <testcase name=\"JettraTestSuite\" classname=\"" + className + "\" time=\"" + time + "\">\n");
                writer.write("    <failure message=\"Test execution failed\">\n");
                // Escape XML for failure details
                String escapedDetails = failureDetails.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                writer.write(escapedDetails + "\n");
                writer.write("    </failure>\n");
                writer.write("  </testcase>\n");
            } else {
                writer.write("  <testcase name=\"JettraTestSuite\" classname=\"" + className + "\" time=\"" + time + "\"/>\n");
            }

            writer.write("</testsuite>\n");
        } catch (IOException e) {
            System.err.println("Failed to write Surefire XML report for " + className + ": " + e.getMessage());
        }
    }
}
