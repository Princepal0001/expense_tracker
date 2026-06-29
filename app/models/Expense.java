package models;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Expense {
    public long id;
    public long userId;
    public String title;
    public String category;
    public BigDecimal amount;
    public LocalDate expenseDate;
    public String note;
}
