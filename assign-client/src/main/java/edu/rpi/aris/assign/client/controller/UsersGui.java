package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.dialog.PasswordResetDialog;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.ServerConfig;
import edu.rpi.aris.assign.client.model.Users;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Pair;
import javafx.util.converter.DefaultStringConverter;

import java.io.IOException;
import java.util.Optional;

public class UsersGui implements TabGui {

    private final CurrentUser userInfo = CurrentUser.getInstance();
    @FXML
    private TableView<Users.UserInfo> userTable;
    @FXML
    private TableColumn<Users.UserInfo, String> username;
    @FXML
    private TableColumn<Users.UserInfo, String> fullName;
    @FXML
    private TableColumn<Users.UserInfo, ServerRole> defaultRole;
    @FXML
    private TableColumn<Users.UserInfo, Button> resetPassword;
    @FXML
    private TableColumn<Users.UserInfo, Button> deleteUser;
    private Users users = new Users(this);
    private Parent root;

    public UsersGui() {
        FXMLLoader loader = new FXMLLoader(AssignmentsGui.class.getResource("/edu/rpi/aris/assign/client/view/users_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @Override
    public void load(boolean reload) {
        users.loadUsers(reload);
    }

    @Override
    public void unload() {
        users.clear();
    }

    @Override
    public void closed() {

    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public String getName() {
        return "Users";
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return new SimpleStringProperty(getName());
    }

    @FXML
    public void initialize() {
        Label placeHolderLbl = new Label();
        userTable.setPlaceholder(placeHolderLbl);
        placeHolderLbl.textProperty().bind(Bindings.createStringBinding(() -> {
            if (userInfo.isLoading())
                return "Loading...";
            else if (!userInfo.isLoggedIn())
                return "Not Logged In";
            else if (users.isLoadError())
                return "Error Loading Users";
            else
                return "No Users... How did you get here?";
        }, userInfo.loginProperty(), userInfo.loadingProperty(), users.loadErrorProperty()));
        userTable.setItems(users.getUsers());
        userInfo.loginProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue)
                users.clear();
        });
        userTable.editableProperty().bind(Bindings.createBooleanBinding(() -> ServerConfig.getPermissions() != null && ServerConfig.getPermissions().hasPermission(userInfo.getDefaultRole(), Perm.USER_EDIT), userInfo.defaultRoleProperty()));
        username.setCellValueFactory(param -> param.getValue().usernameProperty());
        fullName.setCellValueFactory(param -> param.getValue().fullNameProperty());
        fullName.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        fullName.setOnEditCommit(event -> {
            String old = event.getOldValue();
            event.getRowValue().fullNameProperty().set(event.getNewValue());
            users.fullNameChanged(event.getRowValue(), old, event.getNewValue());
        });
        defaultRole.setCellValueFactory(param -> param.getValue().defaultRoleProperty());
        defaultRole.setCellFactory(param -> new ChoiceBoxTableCell<>(ServerConfig.getRoleStringConverter(), ServerConfig.getPermissions().getRoles().toArray(new ServerRole[0])));
        defaultRole.setOnEditCommit(event -> {
            ServerRole old = event.getOldValue();
            event.getRowValue().defaultRoleProperty().set(event.getNewValue());
            users.roleChanged(event.getRowValue(), old, event.getNewValue());
        });
        resetPassword.setCellValueFactory(param -> {
            if (param.getValue().getDefaultRole().getRollRank() >= userInfo.getDefaultRole().getRollRank()) {
                Button btn = new Button("Reset Password");
                btn.setOnAction(e -> resetPassword(param.getValue()));
                return new SimpleObjectProperty<>(btn);
            } else
                return new SimpleObjectProperty<>(null);
        });
        deleteUser.setCellValueFactory(param -> {
            if (param.getValue().getUid() != userInfo.getUser().uid && param.getValue().getDefaultRole().getRollRank() >= userInfo.getDefaultRole().getRollRank()) {
                Button btn = new Button("Delete UserInfo");
                btn.setOnAction(e -> deleteUser(param.getValue()));
                return new SimpleObjectProperty<>(btn);
            } else
                return new SimpleObjectProperty<>(null);
        });
    }

    private void resetPassword(Users.UserInfo info) {
        PasswordResetDialog dialog;
        if (info.getUid() == userInfo.getUser().uid)
            dialog = new PasswordResetDialog("Reset password for " + info.getUsername(), true, false, true);
        else
            dialog = new PasswordResetDialog("Reset password for " + info.getUsername(), false, false, false);
        Optional<Pair<String, String>> result = dialog.showAndWait();
        result.ifPresent(pair -> users.resetPassword(info.getUsername(), pair.getKey(), pair.getValue()));
    }

    private void deleteUser(Users.UserInfo info) {
        AssignGui.getInstance().notImplemented("Delete UserInfo");
    }

    @FXML
    public void addUser() {
        AssignGui.getInstance().notImplemented("Add UserInfo");
    }

}
