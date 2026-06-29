package services;

import models.Expense;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Singleton
public class ExpenseService {

    private final Database database;

    @Inject
    public ExpenseService(Database database) {
        this.database = database;
    }

    public List<Expense> listForUser(long userId) throws Exception {
        String sql = "SELECT id, user_id, title, category, amount, expense_date, note " +
                "FROM expenses " +
                "WHERE user_id = ? " +
                "ORDER BY expense_date DESC, id DESC";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Expense> expenses = new ArrayList<>();
                while (rs.next()) {
                    expenses.add(mapExpense(rs));
                }
                return expenses;
            }
        }
    }

    public Optional<Expense> findForUser(long id, long userId) throws Exception {
        String sql = "SELECT id, user_id, title, category, amount, expense_date, note " +
                "FROM expenses " +
                "WHERE id = ? AND user_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.setLong(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapExpense(rs));
            }
        }
    }

    public long create(long userId, String title, String category, BigDecimal amount, LocalDate expenseDate, String note) throws Exception {
        String sql = "INSERT INTO expenses(user_id, title, category, amount, expense_date, note) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindExpense(ps, userId, title, category, amount, expenseDate, note);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new IllegalStateException("Expense was created but no id was returned.");
    }

    public void update(long id, long userId, String title, String category, BigDecimal amount, LocalDate expenseDate, String note) throws Exception {
        String sql = "UPDATE expenses " +
                "SET title = ?, category = ?, amount = ?, expense_date = ?, note = ? " +
                "WHERE id = ? AND user_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setString(2, category);
            ps.setBigDecimal(3, amount);
            ps.setDate(4, Date.valueOf(expenseDate));
            ps.setString(5, emptyToNull(note));
            ps.setLong(6, id);
            ps.setLong(7, userId);
            ps.executeUpdate();
        }
    }

    public void delete(long id, long userId) throws Exception {
        String sql = "DELETE FROM expenses WHERE id = ? AND user_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public BigDecimal total(List<Expense> expenses) {
        return expenses.stream()
                .map(expense -> expense.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, BigDecimal> totalsByCategory(List<Expense> expenses) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        for (Expense expense : expenses) {
            totals.merge(expense.category, expense.amount, BigDecimal::add);
        }

        return totals;
    }

    private void bindExpense(PreparedStatement ps, long userId, String title, String category, BigDecimal amount, LocalDate expenseDate, String note) throws Exception {
        ps.setLong(1, userId);
        ps.setString(2, title);
        ps.setString(3, category);
        ps.setBigDecimal(4, amount);
        ps.setDate(5, Date.valueOf(expenseDate));
        ps.setString(6, emptyToNull(note));
    }

    private Expense mapExpense(ResultSet rs) throws Exception {
        Expense expense = new Expense();
        expense.id = rs.getLong("id");
        expense.userId = rs.getLong("user_id");
        expense.title = rs.getString("title");
        expense.category = rs.getString("category");
        expense.amount = rs.getBigDecimal("amount");
        expense.expenseDate = rs.getDate("expense_date").toLocalDate();
        expense.note = rs.getString("note");
        return expense;
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}