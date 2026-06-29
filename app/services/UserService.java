package services;

import org.mindrot.jbcrypt.BCrypt;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

@Singleton
public class UserService {

    private final Database database;

    @Inject
    public UserService(Database database) {
        this.database = database;
    }

    // db connection
    public Connection getConnection() throws Exception {
        return database.getConnection();
    }

    public long registerUser(String name, String email, String password) throws Exception {

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        String sql = "INSERT INTO users(name, email, password) VALUES (?, ?, ?)";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, hashedPassword);

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new IllegalStateException("User was created but no id was returned.");
    }

    public Optional<models.User> findByEmail(String email) throws Exception {
        String sql = "SELECT id, name, email, password FROM users WHERE email = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                models.User user = new models.User();
                user.id = rs.getLong("id");
                user.name = rs.getString("name");
                user.email = rs.getString("email");
                user.password = rs.getString("password");
                return Optional.of(user);
            }
        }
    }

    public Optional<models.User> authenticate(String email, String password) throws Exception {
        Optional<models.User> user = findByEmail(email);

        if (user.isEmpty() || !BCrypt.checkpw(password, user.get().password)) {
            return Optional.empty();
        }

        return user;
    }
}
