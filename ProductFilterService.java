package services;
import javafx.concurrent.Task;

import java.sql.*;

public class ProductFilterService {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/javaproj";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Sathvik@123";

    public Task<String> fetchFilteredProducts(String genderFilter, String categoryFilter) {
        return new Task<String>() {
            @Override
            protected String call() throws Exception {
                StringBuilder productHtml = new StringBuilder();
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    String sql = buildFilterQuery(genderFilter, categoryFilter);
                    return executeQueryAndBuildHtml(conn, sql);
                }
            }
        };
    }

    private String buildFilterQuery(String genderFilter, String categoryFilter) {
        String sql = "SELECT itemId, itemName, price, img FROM adults WHERE 1=1";
        if (!genderFilter.isEmpty()) {
            sql += " AND gender IN (" + formatForSql(genderFilter) + ")";
        }
        if (!categoryFilter.isEmpty()) {
            sql += " AND category IN (" + formatForSql(categoryFilter) + ")";
        }
        return sql;
    }

    private String executeQueryAndBuildHtml(Connection conn, String sql) throws SQLException {
        StringBuilder productHtml = new StringBuilder();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (!rs.isBeforeFirst()) {
                productHtml.append("<p>No items found matching your filters.</p>");
            } else {
                while (rs.next()) {
                    productHtml.append(buildProductCard(rs));
                }
            }
        }
        return productHtml.toString();
    }

    private String buildProductCard(ResultSet rs) throws SQLException {
        int itemId = rs.getInt("itemId");
        String itemName = rs.getString("itemName");
        String price = rs.getString("price");
        String image = rs.getString("img");

        return String.format(
                "<div style='border:1px solid #ccc; margin:10px; padding:10px; width: 200px; text-align: center;'>" +
                        "<h3>%s</h3>" +
                        "<p>₹%s</p>" +
                        "<img src='%s' width='150' style='max-width: 100%%; height: auto; display: block; margin: 0 auto;'><br>" +
                        "<button onclick='window.app.addToCart(%d)'>Add to Cart</button>" + // Modified line
                        "</div>",
                itemName, price, image, itemId
        );
    }

    // Helper methods
    private String formatForSql(String commaSeparated) {
        String[] parts = commaSeparated.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append("'").append(parts[i].trim()).append("'");
            if (i != parts.length - 1) sb.append(",");
        }
        return sb.toString();
    }

    public String escapeJavaScriptString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}