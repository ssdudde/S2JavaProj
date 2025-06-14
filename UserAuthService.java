package services;
import java.sql.*;

public class UserAuthService {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/javaproj";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Sathvik@123";

    public int registerUser(String username, String password, String phone) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Check existing user
            if (userExists(username, phone, conn)) return -1;

            // Insert new user
            String sql = "INSERT INTO users (userName, password, phone) VALUES (?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, phone);

            ps.executeUpdate();
            ResultSet generatedKeys = ps.getGeneratedKeys();
            return generatedKeys.next() ? generatedKeys.getInt(1) : -1;
        }
    }

    public int loginUser(String username, String password) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT id FROM users WHERE userName = ? AND password = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, username);
            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    private boolean userExists(String username, String phone, Connection conn) throws SQLException {
        String sql = "SELECT id FROM users WHERE userName = ? OR phone = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, username);
        ps.setString(2, phone);
        return ps.executeQuery().next();
    }
}
