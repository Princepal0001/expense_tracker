package controllers;

import models.Expense;
import play.mvc.*;
import services.ExpenseService;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExpenseController extends Controller {

    private final ExpenseService expenseService;

    @Inject
    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    public Result index(Http.Request request) throws Exception {
        Optional<Long> userId = currentUserId(request);
        if (userId.isEmpty()) {
            return redirect("/login");
        }

        List<Expense> expenses = expenseService.listForUser(userId.get());
        return ok(views.html.expenses.render(
                expenses,
                expenseService.total(expenses),
                expenseService.totalsByCategory(expenses),
                request.session().getOptional("userName").orElse("You")
        ));
    }

    public Result create(Http.Request request) throws Exception {
        Optional<Long> userId = currentUserId(request);
        if (userId.isEmpty()) {
            return redirect("/login");
        }

        Map<String, String[]> data = request.body().asFormUrlEncoded();
        expenseService.create(
                userId.get(),
                formValue(data, "title"),
                formValue(data, "category"),
                parseAmount(formValue(data, "amount")),
                parseDate(formValue(data, "expenseDate")),
                formValue(data, "note")
        );

        return redirect("/expenses");
    }

    public Result edit(long id, Http.Request request) throws Exception {
        Optional<Long> userId = currentUserId(request);
        if (userId.isEmpty()) {
            return redirect("/login");
        }

        Optional<Expense> expense = expenseService.findForUser(id, userId.get());
        if (expense.isEmpty()) {
            return notFound("Expense not found.");
        }

        return ok(views.html.editExpense.render(expense.get()));
    }

    public Result update(long id, Http.Request request) throws Exception {
        Optional<Long> userId = currentUserId(request);
        if (userId.isEmpty()) {
            return redirect("/login");
        }

        Map<String, String[]> data = request.body().asFormUrlEncoded();
        expenseService.update(
                id,
                userId.get(),
                formValue(data, "title"),
                formValue(data, "category"),
                parseAmount(formValue(data, "amount")),
                parseDate(formValue(data, "expenseDate")),
                formValue(data, "note")
        );

        return redirect("/expenses");
    }

    public Result delete(long id, Http.Request request) throws Exception {
        Optional<Long> userId = currentUserId(request);
        if (userId.isEmpty()) {
            return redirect("/login");
        }

        expenseService.delete(id, userId.get());
        return redirect("/expenses");
    }

    private Optional<Long> currentUserId(Http.Request request) {
        return request.session().getOptional("userId").map(Long::parseLong);
    }

    private String formValue(Map<String, String[]> data, String key) {
        if (data == null || !data.containsKey(key) || data.get(key).length == 0) {
            return "";
        }
        return data.get(key)[0].trim();
    }

    private BigDecimal parseAmount(String value) {
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private LocalDate parseDate(String value) {
        return value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
    }
}
