package services;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.sql.SQLException;


public class ClothingWebViewApp extends Application {
    private static Stage primaryStage;
    private UserAuthService authService = new UserAuthService();
    public static Stage getMainStage() {
        return primaryStage; // Return your main application stage
    }

    // In JavaBridge constructor

    @Override
    public void start(Stage primaryStage) {
        ClothingWebViewApp.primaryStage = primaryStage;
        // Login UI
        Label userLabel = new Label("Username:");
        TextField userField = new TextField();
        Label passLabel = new Label("Password:");
        PasswordField passField = new PasswordField();
        Button loginButton = new Button("Login");
        Button registerLinkButton = new Button("Don't have an account? Register here!");

        VBox loginLayout = new VBox(10, userLabel, userField, passLabel, passField, loginButton, new Separator(), registerLinkButton);
        loginLayout.setPadding(new Insets(20));
        Scene loginScene = new Scene(loginLayout, 300, 250);

        Stage loginStage = new Stage();
        loginStage.setTitle("Login");
        loginStage.setScene(loginScene);
        loginStage.show();

        // Login handler
        loginButton.setOnAction(e -> {
            String username = userField.getText();
            String password = passField.getText();

            Task<Integer> loginTask = new Task<Integer>() {
                @Override
                protected Integer call() throws Exception {
                    return authService.loginUser(username, password);
                }
            };

            loginTask.setOnSucceeded(loginEvent -> {
                int userId = loginTask.getValue();
                if (userId != -1) {
                    loginStage.close();
                    showMainWindow(primaryStage, userId);
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid credentials! Please try again or register.");
                    alert.show();
                }
            });

            loginTask.setOnFailed(loginEvent -> {
                loginTask.getException().printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR, "Login error: " + loginTask.getException().getMessage());
                alert.show();
            });

            new Thread(loginTask).start();
        });

        // Registration handler
        registerLinkButton.setOnAction(e -> {
            showRegistrationWindow(loginStage);
        });
    }

    private void showRegistrationWindow(Stage parentStage) {
        Stage registerStage = new Stage();
        registerStage.initModality(Modality.APPLICATION_MODAL);
        registerStage.initOwner(parentStage);
        registerStage.setTitle("Register New Account");

        Label userLabel = new Label("Username:");
        TextField userField = new TextField();
        Label passLabel = new Label("Password:");
        PasswordField passField = new PasswordField();
        Label mobileLabel = new Label("Mobile No.:");
        TextField mobileField = new TextField();
        Button registerButton = new Button("Register");

        VBox registerLayout = new VBox(10, userLabel, userField, passLabel, passField, mobileLabel, mobileField, registerButton);
        registerLayout.setPadding(new Insets(20));
        Scene registerScene = new Scene(registerLayout, 350, 300);

        registerButton.setOnAction(e -> {
            String username = userField.getText();
            String password = passField.getText();
            String phone = mobileField.getText();

            if (username.isEmpty() || password.isEmpty() || phone.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "All fields are required!");
                alert.show();
                return;
            }

            if (!phone.matches("\\d{10}")) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please enter a valid 10-digit mobile number.");
                alert.show();
                return;
            }

            Task<Integer> registerTask = new Task<Integer>() {
                @Override
                protected Integer call() throws Exception {
                    return authService.registerUser(username, password, phone);
                }
            };

            registerTask.setOnSucceeded(regEvent -> {
                int newUserId = registerTask.getValue();
                if (newUserId != -1) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Registration successful! You can now log in.");
                    alert.showAndWait();
                    registerStage.close();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Registration failed. Username or Mobile number might already exist, or there was a database error.");
                    alert.show();
                }
            });

            registerTask.setOnFailed(regEvent -> {
                registerTask.getException().printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR, "Registration error: " + registerTask.getException().getMessage());
                alert.show();
            });

            new Thread(registerTask).start();
        });

        registerStage.setScene(registerScene);
        registerStage.show();
    }

    private void showMainWindow(Stage stage, int userId) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        JavaBridge bridge = new JavaBridge(userId, this, engine, ClothingWebViewApp.getMainStage());
        // Initial HTML with filters, cart button, and JavaScript functions
        String initialHtml = "<!DOCTYPE html><html><head>"
                + "<style>"
                + "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; background-color: #f4f7f6; color: #333; }"
                + ".filter-section { background-color: #ffffff; margin: 20px; padding: 20px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.05); }"
                + ".filter-section h3 { color: #2c3e50; margin-top: 0; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px; margin-bottom: 15px; }"
                + ".filter-section > div { margin-bottom: 15px; }"
                + ".filter-section strong { display: block; margin-bottom: 8px; color: #555; }"
                + ".filter-section label { margin-right: 15px; font-size: 14px; cursor: pointer; }"
                + ".filter-section input[type='checkbox'] { transform: scale(1.2); margin-right: 5px; accent-color: #007bff; }"
                + "button { background-color: #007bff; color: white; padding: 10px 20px; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; transition: background-color 0.3s ease; }"
                + "button:hover { background-color: #0056b3; }"
                + "#viewCartBtn { position: fixed; top: 20px; right: 20px; padding: 12px 25px; background-color: #28a745; z-index: 1000; box-shadow: 0 4px 8px rgba(0,0,0,0.1); border-radius: 25px; }"
                + "#viewCartBtn:hover { background-color: #218838; }"
                + "#productList { display: flex; flex-wrap: wrap; gap: 25px; justify-content: center; padding: 20px; }"
                + ".product-item { background-color: #ffffff; border: 1px solid #e0e0e0; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); padding: 15px; text-align: center; width: 220px; transition: transform 0.2s ease, box-shadow 0.2s ease; }"
                + ".product-item:hover { transform: translateY(-5px); box-shadow: 0 6px 16px rgba(0,0,0,0.12); }"
                + ".product-item img { max-width: 100%; height: 180px; object-fit: cover; border-radius: 8px; margin-bottom: 10px; }"
                + ".product-item h3 { font-size: 1.2em; color: #333; margin-bottom: 8px; }"
                + ".product-item p { font-size: 0.9em; color: #666; margin-bottom: 10px; }"
                + ".product-item .price { font-size: 1.1em; color: #007bff; font-weight: bold; margin-bottom: 15px; }"
                + ".product-item button { width: 90%; padding: 10px; background-color: #17a2b8; }"
                + ".product-item button:hover { background-color: #138496; }"
                + "</style>"
                + "</head><body>"
                + "<button id='viewCartBtn' onclick='viewCart()'>View Cart</button>"
                + "<div class='filter-section'>"
                + "  <h3>Filter by:</h3>"
                + "  <div>"
                + "    <strong>Gender:</strong>"
                + "    <label><input type='checkbox' name='gender' value='Men'> Men</label>"
                + "    <label><input type='checkbox' name='gender' value='Women'> Women</label>"
                + "  </div>"
                + "  <div>"
                + "    <strong>Category:</strong>"
                + "    <label><input type='checkbox' name='category' value='Top'> Top</label>"
                + "    <label><input type='checkbox' name='category' value='Bottom'> Bottom</label>"
                + "  </div>"
                + "  <button onclick='applyFilters()' style='margin-top:10px'>Apply Filters</button>"
                + "</div>"
                + "<div id='productList'></div>"

                +"<script>"
                + "function updateProductList(newHtml) { "
                + "  document.getElementById('productList').innerHTML = newHtml; "
                + "}"
                + "function applyFilters() { "
                + "  const genders = Array.from(document.querySelectorAll('input[name=gender]:checked')).map(cb => cb.value);"
                + "  const cats = Array.from(document.querySelectorAll('input[name=category]:checked')).map(cb => cb.value);"
                + "  window.app.filterItems(genders.join(','), cats.join(','));"
                + "}"
                + "function viewCart() { window.app.viewCart(); }"
                + "function addToCart(id) {  alert(\"addToCart called with id=\" + id);\n" +
                "    if(window.app && window.app.addToCart) {\n" +
                "        window.app.addToCart(id);\n" +
                "    } }"
                + "</script>"
                + "</body></html>";


        engine.loadContent(initialHtml);

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("app", bridge);
                bridge.loadInitialProducts();
            }
        });

        stage.setTitle("Clothing Store");
        stage.setScene(new Scene(webView, 800, 600));
        stage.show();
    }


}