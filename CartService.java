package services;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CartService {
    private final int userId;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/javaproj";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Sathvik@123";

    public CartService(int userId) {
        this.userId = userId;
    }

    public void addToCart(int itemId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String checkSql = "SELECT noofitem FROM cart WHERE itemid = ? AND userid = ?";
            PreparedStatement checkPs = conn.prepareStatement(checkSql);
            checkPs.setInt(1, itemId);
            checkPs.setInt(2, userId);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next()) {
                int currentQty = rs.getInt("noofitem");
                String updateSql = "UPDATE cart SET noofitem = ? WHERE itemid = ? AND userid = ?";
                PreparedStatement updatePs = conn.prepareStatement(updateSql);
                updatePs.setInt(1, currentQty + 1);
                updatePs.setInt(2, itemId);
                updatePs.setInt(3, userId);
                updatePs.executeUpdate();
            } else {
                PreparedStatement ps = conn.prepareStatement("INSERT INTO cart (itemid, userid, noofitem) VALUES (?, ?, 1)");
                ps.setInt(1, itemId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        }
    }

    public void removeItemFromCart(int itemId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "DELETE FROM cart WHERE itemid = ? AND userid = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, itemId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void clearCart() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "DELETE FROM cart WHERE userid = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public List<CartItem> getCartItems() throws SQLException {
        List<CartItem> items = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT a.itemId, a.itemName, a.price, a.img, c.noofitem " +
                    "FROM adults a JOIN cart c ON a.itemId = c.itemid WHERE c.userid = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new CartItem(
                        rs.getInt("itemId"),
                        rs.getString("itemName"),
                        rs.getDouble("price"),
                        rs.getString("img"),
                        rs.getInt("noofitem")
                ));
            }
        }
        return items;
    }

    // Helper class for cart items
    public static class CartItem {
        public int itemId;
        public String itemName;
        public double price;
        public String img;
        public int quantity;

        public CartItem(int itemId, String itemName, double price, String img, int quantity) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.price = price;
            this.img = img;
            this.quantity = quantity;
        }
    }
}
