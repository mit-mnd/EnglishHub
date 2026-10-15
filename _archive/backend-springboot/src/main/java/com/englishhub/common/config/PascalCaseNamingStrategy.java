package com.englishhub.common.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Map tên field camelCase sang cột PascalCase theo quy ước database (fullName -> FullName).
 * Tên bảng và join column được khai báo tường minh bằng @Table / @JoinColumn.
 */
public class PascalCaseNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        return capitalize(name);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        return capitalize(name);
    }

    private static Identifier capitalize(Identifier identifier) {
        if (identifier == null || identifier.getText().isEmpty()) {
            return identifier;
        }
        String text = identifier.getText();
        return Identifier.toIdentifier(Character.toUpperCase(text.charAt(0)) + text.substring(1), identifier.isQuoted());
    }
}
