package controllers;

import models.User;
import play.mvc.*;
import services.UserService;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

public class AuthController extends Controller {

    private final UserService userService;

    @Inject
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    public Result showRegister() {
        return ok(views.html.register.render());
    }

    public Result showLogin() {
        return ok(views.html.login.render(null));
    }

    public Result register(Http.Request request) throws Exception {

        Map<String, String[]> data = request.body().asFormUrlEncoded();

        String name = formValue(data, "name");
        String email = formValue(data, "email");
        String password = formValue(data, "password");

        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            return badRequest(views.html.register.render());
        }

        long userId = userService.registerUser(name, email, password);

        return redirect("/expenses")
                .addingToSession(request, "userId", Long.toString(userId))
                .addingToSession(request, "userName", name);
    }

    public Result login(Http.Request request) throws Exception {
        Map<String, String[]> data = request.body().asFormUrlEncoded();

        String email = formValue(data, "email");
        String password = formValue(data, "password");

//        NullPointerException without optional with is we can use is empty and present
        Optional<User> user = userService.authenticate(email, password);

        if (user.isEmpty()) {
            return unauthorized(views.html.login.render("Invalid email or password."));
        }

        return redirect("/expenses")
                .addingToSession(request, "userId", Long.toString(user.get().id))
                .addingToSession(request, "userName", user.get().name);
    }

    public Result logout(Http.Request request) {
        return redirect("/login")
                .removingFromSession(request, "userId")
                .removingFromSession(request, "userName");
    }

    private String formValue(Map<String, String[]> data, String key) {
        if (data == null || !data.containsKey(key) || data.get(key).length == 0) {
            return "";
        }
        return data.get(key)[0].trim();
    }
}
