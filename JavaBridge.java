package services;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PdfPTable;

import java.io.FileOutputStream;
import java.sql.*;
import java.util.List;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

public class JavaBridge extends UserAuthService {
    private final int userId;
    private final CartService cartService;
    private final ProductFilterService productFilterService;
    private final WebEngine mainEngine;
    private WebEngine currentCartEngine;
    private Stage currentCartStage;
    private final Stage mainStage;

    public JavaBridge(int userId, ClothingWebViewApp appInstance, WebEngine mainEngine, Stage mainStage) {
        this.userId = userId;
        this.cartService = new CartService(userId);
        this.productFilterService = new ProductFilterService();
        this.mainEngine = mainEngine;
        this.mainStage = mainStage;
    }

    public void loadInitialProducts() {
        filterItems("", ""); // Load all products by default
    }
    // ================== PRODUCT FILTERING ==================
    public void filterItems(String genderFilter, String categoryFilter) {
        Task<String> filterTask = productFilterService.fetchFilteredProducts(genderFilter, categoryFilter);

        filterTask.setOnSucceeded(e -> {
            String escapedHtml = productFilterService.escapeJavaScriptString(filterTask.getValue());
            Platform.runLater(() -> {
                mainEngine.executeScript("updateProductList('" + escapedHtml + "')");
            });
        });

        filterTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                mainEngine.executeScript(
                        "document.getElementById('productList').innerHTML = " +
                                "'<p style=\"color:red;\">Error loading products!</p>'"
                );
            });
        });

        new Thread(filterTask).start();
    }

    // ================== CART OPERATIONS ==================
    public void addToCart(int itemId) {
        Task<Void> task = new Task<Void>() {
            @Override protected Void call() throws Exception {
                cartService.addToCart(itemId);
                return null;
            }
        };
        new Thread(task).start();
    }

    public void removeItemFromCart(int itemId) {
        Task<Void> task = new Task<Void>() {
            @Override protected Void call() throws Exception {
                cartService.removeItemFromCart(itemId);
                refreshCart();
                return null;
            }
        };
        new Thread(task).start();
    }

    // ================== CART UI MANAGEMENT ==================
    public void viewCart() {
        Platform.runLater(() -> {
            if (currentCartStage == null) {
                initializeCartWindow();
            }
            if (!currentCartStage.isShowing()) {
                currentCartStage.show();
            }
            refreshCart();
        });
    }

    private void initializeCartWindow() {
        currentCartStage = new Stage();
        currentCartStage.initOwner(mainStage);
        WebView cartView = new WebView();
        currentCartEngine = cartView.getEngine();

        currentCartEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) currentCartEngine.executeScript("window");
                window.setMember("app", this);
            }
        });

        currentCartStage.setScene(new Scene(cartView, 600, 700));
        currentCartStage.setTitle("Your Cart");
    }

    private void refreshCart() {
        Task<String> task = new Task<String>() {
            @Override protected String call() throws Exception {
                return buildCartHtml(cartService.getCartItems());
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                currentCartEngine.loadContent(task.getValue());
                currentCartStage.toFront();
            });
        });

        new Thread(task).start();
    }

    private String buildCartHtml(List<CartService.CartItem> items) {
        StringBuilder html = new StringBuilder()
                .append("<html><head><style>"
                        + "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background-color: #f4f7f6; color: #333; }"
                        + "h2 { text-align: center; color: #2c3e50; margin-bottom: 30px; font-size: 2em; }"
                        + ".cart-item { display: flex; align-items: center; background-color: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px; margin-bottom: 15px; padding: 15px; box-shadow: 0 2px 5px rgba(0,0,0,0.05); transition: transform 0.2s ease; }"
                        + ".cart-item:hover { transform: translateY(-3px); box-shadow: 0 4px 10px rgba(0,0,0,0.1); }"
                        + ".cart-item img { width: 100px; height: 100px; object-fit: cover; border-radius: 5px; margin-right: 20px; border: 1px solid #ddd; }"
                        + ".cart-item-details { flex-grow: 1; }"
                        + ".cart-item h3 { margin: 0 0 5px 0; color: #333; font-size: 1.3em; }"
                        + ".cart-item p { margin: 0; color: #666; font-size: 0.95em; }"
                        + ".cart-item button { background-color: #dc3545; color: white; padding: 8px 15px; border: none; border-radius: 5px; cursor: pointer; font-size: 0.9em; transition: background-color 0.3s ease; }"
                        + ".cart-item button:hover { background-color: #c82333; }"
                        + "h3.total { text-align: right; margin-top: 30px; font-size: 1.8em; color: #007bff; border-top: 2px solid #e0e0e0; padding-top: 20px; }"
                        + "button.place-order-btn { display: block; width: 100%; padding: 15px; background-color: #28a745; color: white; border: none; border-radius: 8px; font-size: 1.2em; cursor: pointer; transition: background-color 0.3s ease; margin-top: 20px; }"
                        + "button.place-order-btn:hover { background-color: #218838; }"
                        + "</style></head><body>")
                .append("<h2>Your Cart</h2>");

        double total = 0.0;
        if (items.isEmpty()) {
            html.append("<p style='text-align: center; color: #888; font-size: 1.1em;'>Your cart is empty.</p>");
        } else {
            for (CartService.CartItem item : items) {
                html.append("<div class='cart-item'>")
                        .append("<img src='").append(item.img).append("' alt='").append(item.itemName).append("'>")
                        .append("<div class='cart-item-details'>")
                        .append("<h3>").append(item.itemName).append("</h3>")
                        .append("<p>Quantity: ").append(item.quantity).append("</p>")
                        .append("<p>Price: ₹").append(String.format("%.2f", item.price)).append("</p>")
                        .append("</div>")
                        .append("<button onclick='app.removeItemFromCart(").append(item.itemId).append(")'>Remove</button>")
                        .append("</div>");
                total += item.price * item.quantity;
            }
        }

        html.append("<h3 class='total'>Total: ₹").append(String.format("%.2f", total)).append("</h3>");
        if (!items.isEmpty()) {
            html.append("<button class='place-order-btn' onclick='app.placeOrder()'>Place Order</button>");
        }
        html.append("</body></html>");

        return html.toString();
    }

    // ================== ORDER PROCESSING ==================
    public void placeOrder() {
        Task<Void> task = new Task<Void>() {
            @Override protected Void call() throws Exception {
                OrderService orderService = new OrderService(userId);
                orderService.placeOrder();
                cartService.clearCart();
                Platform.runLater(() -> {
                    currentCartEngine.loadContent("<h2 style='text-align: center; color: #28a745; margin-top: 50px;'>Order Placed Successfully!</h2><p style='text-align: center; color: #666; font-size: 1.1em;'>Thank you for your purchase.</p>");
                    mainEngine.executeScript("alert('Order completed!')");
                });
                return null;
            }
        };
        new Thread(task).start();
    }
}