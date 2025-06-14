package services;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.control.Alert;

public class OrderService {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/javaproj";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Sathvik@123";
    private final int userId;

    public OrderService(int userId) {
        this.userId = userId;
    }

    public void placeOrder() {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Connection conn = null;
                try {
                    conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/javaproj", "root", "Sathvik@123");
                    conn.setAutoCommit(false); // Start transaction

                    // 1. Calculate total
                    double total = 0.0;
                    try (PreparedStatement psTotal = conn.prepareStatement(
                            "SELECT SUM(a.price * c.noofitem) AS total " +
                                    "FROM adults a JOIN cart c ON a.itemId = c.itemid WHERE c.userid = ?")) {
                        psTotal.setInt(1, userId);
                        try (ResultSet rsTotal = psTotal.executeQuery()) {
                            if (rsTotal.next()) total = rsTotal.getDouble("total");
                        }
                    }

                    if (total == 0) {
//                        Platform.runLater(() -> showError("Empty Cart", "Your cart is empty!"));
//                        return null;
                    }

                    // 2. Create order record
                    int orderId;
                    try (PreparedStatement psOrder = conn.prepareStatement(
                            "INSERT INTO orders (userid, total_amount, order_date) VALUES (?, ?, NOW())",
                            Statement.RETURN_GENERATED_KEYS)) {
                        psOrder.setInt(1, userId);
                        psOrder.setDouble(2, total);
                        psOrder.executeUpdate();

                        try (ResultSet rsOrderId = psOrder.getGeneratedKeys()) {
                            if (!rsOrderId.next()) throw new SQLException("No generated order ID");
                            orderId = rsOrderId.getInt(1);
                        }
                    }

                    // 3. Move cart items to order_items
                    try (PreparedStatement psItems = conn.prepareStatement(
                            "SELECT itemid, noofitem FROM cart WHERE userid = ?")) {
                        psItems.setInt(1, userId);

                        try (ResultSet rsItems = psItems.executeQuery();
                             PreparedStatement insertItem = conn.prepareStatement(
                                     "INSERT INTO order_items (orderid, itemid, quantity) VALUES (?, ?, ?)")) {

                            while (rsItems.next()) {
                                insertItem.setInt(1, orderId);
                                insertItem.setInt(2, rsItems.getInt("itemid"));
                                insertItem.setInt(3, rsItems.getInt("noofitem"));
                                insertItem.addBatch();
                            }
                            insertItem.executeBatch();
                        }
                    }

                    // 4. Clear cart
                    try (PreparedStatement clearCart = conn.prepareStatement(
                            "DELETE FROM cart WHERE userid = ?")) {
                        clearCart.setInt(1, userId);
                        clearCart.executeUpdate();
                    }

                    conn.commit(); // Commit transaction
                    System.out.println("✅ Order placed successfully. ID: " + orderId);

                    // 5. Generate PDF
                    generateBillPdf(orderId, total);

                } catch (SQLException e) {
                    // Rollback on error
                    if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
                    System.err.println("❌ Order placement failed: " + e.getMessage());
                    e.printStackTrace();
//                    Platform.runLater(() ->
//                            showError("Order Failed", "Could not complete order: " + e.getMessage()));
                } finally {
                    if (conn != null) try { conn.close(); } catch (SQLException e) {}
                }
                return null;
            }
        };

        task.setOnFailed(e ->
                System.err.println("Order task failed: " + task.getException().getMessage()));

        new Thread(task).start();
    }



    public void generateBillPdf(int orderId, double total) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Document doc = new Document();
                FileOutputStream fos = null;
                PdfWriter writer = null;
                try {
                    // 1. Prepare output path
                    String downloadsDir = System.getProperty("user.home") + "/Downloads";
                    String outputPath = downloadsDir + "/Order_" + orderId + "_Bill.pdf";
                    new File(downloadsDir).mkdirs(); // Ensure directory exists

                    // 2. Initialize PDF writer
                    fos = new FileOutputStream(outputPath);
                    writer = PdfWriter.getInstance(doc, fos);
                    doc.open();

                    // 3. Header Section
                    doc.add(new Paragraph("---------------------------------------------------------------------------"));
                    doc.add(new Paragraph("               EAST COAST CLOTHING STORE           "));
                    doc.add(new Paragraph("---------------------------------------------------------------------------"));
                    doc.add(new Paragraph("Order ID: " + orderId));
                    doc.add(new Paragraph("Date: " + new java.util.Date()));
                    doc.add(new Paragraph(" "));

                    // 4. Items Table
                    PdfPTable table = new PdfPTable(4);
                    table.setWidthPercentage(100);
                    table.setHeaderRows(1);
                    table.addCell("Item Name");
                    table.addCell("Price (₹)");
                    table.addCell("Quantity");
                    table.addCell("Subtotal (₹)");
                    table.completeRow();

                    // 5. Database Query
                    System.out.println("[DEBUG] Fetching order items for order: " + orderId);
                    try (Connection conn = DriverManager.getConnection(
                            "jdbc:mysql://localhost:3306/javaproj", "root", "Sathvik@123")) {

                        String sql = "SELECT a.itemName, a.price, oi.quantity "
                                + "FROM order_items oi "
                                + "JOIN adults a ON a.itemId = oi.itemid "
                                + "WHERE oi.orderid = ?";

                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, orderId);
                            try (ResultSet rs = ps.executeQuery()) {
                                int rowCount = 0;
                                while (rs.next()) {
                                    rowCount++;
                                    String itemName = rs.getString("itemName");
                                    double price = rs.getDouble("price");
                                    int qty = rs.getInt("quantity");
                                    double subtotal = price * qty;

                                    // 6. Add to table
                                    table.addCell(itemName);
                                    table.addCell(String.format("%.2f", price));
                                    table.addCell(String.valueOf(qty));
                                    table.addCell(String.format("%.2f", subtotal));
                                    table.completeRow();
                                }
                                System.out.println("[DEBUG] Added " + rowCount + " items to PDF");

                                // 7. Handle empty orders
                                if (rowCount == 0) {
                                    table.addCell("No items found for this order");
                                    table.addCell("");
                                    table.addCell("");
                                    table.addCell("");
                                    table.completeRow();
                                }
                            }
                        }
                    }

                    // 8. Add table to document
                    doc.add(table);

                    // 9. Footer Section
                    doc.add(new Paragraph(" "));
                    doc.add(new Paragraph("Total: ₹" + String.format("%.2f", total)));
                    doc.add(new Paragraph("--------------------------------------------------"));
                    doc.add(new Paragraph("       Thank you for shopping with us!       "));
                    doc.add(new Paragraph("--------------------------------------------------"));

                    System.out.println("[SUCCESS] PDF generated at: " + outputPath);

                } catch (SQLException e) {
                    System.err.println("[DATABASE ERROR] " + e.getMessage());
//                    Platform.runLater(() -> System.out.println("Database Error", "Could not load order details"));
                } catch (DocumentException | IOException e) {
                    System.err.println("[PDF ERROR] " + e.getMessage());
//                    Platform.runLater(() -> showError("PDF Error", "Failed to generate invoice"));
                } finally {
                    // 10. Cleanup resources
                    if (doc != null && doc.isOpen()) doc.close();
                    if (writer != null) writer.close();
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            System.err.println("[IO ERROR] " + e.getMessage());
                        }
                    }
                }
                return null;
            }
        };
        new Thread(task).start();
    }
}
