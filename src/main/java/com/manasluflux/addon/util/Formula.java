package com.manasluflux.addon.util;

/**
 * A very small arithmetic evaluator, just enough for the Cobweb Bypass graph timer: the user
 * types a formula of {@code t} (seconds spent in the web) and this turns it into a number.
 *
 * <p>Supported: {@code + - * / ^}, unary minus, parentheses, the constants {@code t}, {@code e},
 * {@code pi}, and the functions {@code exp, log, ln, sqrt, abs, sign, floor, ceil, round, min,
 * max, sin, cos, tan}. No reflection, no scripting engine - the expression is parsed by hand, so
 * a bad formula is only ever an exception, never anything that can run.
 *
 * <p>Evaluation is stateless: {@link #eval(String, double)} re-parses every call. That is fine at
 * the rate this is used (once per tick, with a formula of a couple of dozen characters).
 */
public final class Formula {
    private Formula() {
    }

    /** Anything wrong with the formula: syntax, unknown name, division by zero, NaN result. */
    public static class FormulaException extends RuntimeException {
        public FormulaException(String message) {
            super(message);
        }
    }

    /** Evaluates {@code expression} with {@code t} bound to {@code t}. */
    public static double eval(String expression, double t) {
        if (expression == null || expression.isBlank()) throw new FormulaException("the formula is empty");

        Parser parser = new Parser(expression, t);
        double value = parser.expression();
        parser.skipSpace();

        if (!parser.atEnd()) throw new FormulaException("unexpected \"" + parser.rest() + "\"");
        if (!Double.isFinite(value)) throw new FormulaException("the result is not a normal number");

        return value;
    }

    private static final class Parser {
        private final String source;
        private final double t;
        private int i;

        private Parser(String source, double t) {
            this.source = source;
            this.t = t;
        }

        private boolean atEnd() {
            return i >= source.length();
        }

        private String rest() {
            return source.substring(Math.min(i, source.length()));
        }

        private void skipSpace() {
            while (!atEnd() && Character.isWhitespace(source.charAt(i))) i++;
        }

        private boolean eat(char c) {
            skipSpace();
            if (!atEnd() && source.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        // expression := term (('+' | '-') term)*
        private double expression() {
            double value = term();

            while (true) {
                if (eat('+')) value += term();
                else if (eat('-')) value -= term();
                else return value;
            }
        }

        // term := power (('*' | '/' | '%') power)*
        private double term() {
            double value = power();

            while (true) {
                if (eat('*')) value *= power();
                else if (eat('/')) {
                    double divisor = power();
                    if (divisor == 0) throw new FormulaException("division by zero");
                    value /= divisor;
                } else if (eat('%')) {
                    double divisor = power();
                    if (divisor == 0) throw new FormulaException("division by zero");
                    value %= divisor;
                } else return value;
            }
        }

        // power := unary ('^' power)?   - right associative, so 2^3^2 is 2^9
        private double power() {
            double base = unary();
            if (!eat('^')) return base;

            double exponent = power();

            // A negative base with a fractional exponent is NaN in Java; say so instead of
            // silently handing the module a NaN timer.
            double result = Math.pow(base, exponent);
            if (!Double.isFinite(result)) {
                throw new FormulaException("^ produced a non-number (negative base with a fraction?)");
            }
            return result;
        }

        private double unary() {
            skipSpace();
            if (eat('+')) return unary();
            if (eat('-')) return -unary();
            return primary();
        }

        private double primary() {
            skipSpace();
            if (atEnd()) throw new FormulaException("the formula ends too early");

            if (eat('(')) {
                double value = expression();
                if (!eat(')')) throw new FormulaException("missing \")\"");
                return value;
            }

            char c = source.charAt(i);

            if (Character.isDigit(c) || c == '.') return number();
            if (Character.isLetter(c) || c == '_') return name();

            throw new FormulaException("unexpected \"" + c + "\"");
        }

        private double number() {
            int start = i;

            while (!atEnd() && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;

            // 1e-3 style exponents.
            if (!atEnd() && (source.charAt(i) == 'e' || source.charAt(i) == 'E')) {
                int save = i;
                i++;
                if (!atEnd() && (source.charAt(i) == '+' || source.charAt(i) == '-')) i++;
                if (!atEnd() && Character.isDigit(source.charAt(i))) {
                    while (!atEnd() && Character.isDigit(source.charAt(i))) i++;
                } else {
                    i = save;
                }
            }

            try {
                return Double.parseDouble(source.substring(start, i));
            } catch (NumberFormatException e) {
                throw new FormulaException("\"" + source.substring(start, i) + "\" is not a number");
            }
        }

        private double name() {
            int start = i;
            while (!atEnd() && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;

            String name = source.substring(start, i).toLowerCase(java.util.Locale.ROOT);
            skipSpace();

            if (eat('(')) {
                return call(name, arguments(name));
            }

            return switch (name) {
                case "t" -> t;
                case "e" -> Math.E;
                case "pi" -> Math.PI;
                default -> throw new FormulaException("unknown name \"" + name + "\" (use t, e, pi or a function)");
            };
        }

        private double[] arguments(String function) {
            java.util.List<Double> values = new java.util.ArrayList<>();

            skipSpace();
            if (eat(')')) throw new FormulaException("\"" + function + "\" needs a value");

            values.add(expression());
            while (eat(',')) values.add(expression());
            if (!eat(')')) throw new FormulaException("missing \")\" after \"" + function + "(\"");

            double[] args = new double[values.size()];
            for (int k = 0; k < args.length; k++) args[k] = values.get(k);
            return args;
        }

        private double call(String function, double[] args) {
            switch (function) {
                case "exp":
                    need(function, args, 1);
                    return Math.exp(args[0]);
                case "log":
                case "ln":
                    need(function, args, 1);
                    if (args[0] <= 0) throw new FormulaException("log of a number that is not positive");
                    return Math.log(args[0]);
                case "sqrt":
                    need(function, args, 1);
                    if (args[0] < 0) throw new FormulaException("sqrt of a negative number");
                    return Math.sqrt(args[0]);
                case "abs":
                    need(function, args, 1);
                    return Math.abs(args[0]);
                case "sign":
                    need(function, args, 1);
                    return Math.signum(args[0]);
                case "floor":
                    need(function, args, 1);
                    return Math.floor(args[0]);
                case "ceil":
                    need(function, args, 1);
                    return Math.ceil(args[0]);
                case "round":
                    need(function, args, 1);
                    return Math.round(args[0]);
                case "sin":
                    need(function, args, 1);
                    return Math.sin(args[0]);
                case "cos":
                    need(function, args, 1);
                    return Math.cos(args[0]);
                case "tan":
                    need(function, args, 1);
                    return Math.tan(args[0]);
                case "min": {
                    if (args.length == 0) throw new FormulaException("min needs a value");
                    double value = args[0];
                    for (double arg : args) value = Math.min(value, arg);
                    return value;
                }
                case "max": {
                    if (args.length == 0) throw new FormulaException("max needs a value");
                    double value = args[0];
                    for (double arg : args) value = Math.max(value, arg);
                    return value;
                }
                default:
                    throw new FormulaException("unknown function \"" + function + "\"");
            }
        }

        private void need(String function, double[] args, int count) {
            if (args.length != count) {
                throw new FormulaException(function + " takes " + count + " value(s)");
            }
        }
    }
}
