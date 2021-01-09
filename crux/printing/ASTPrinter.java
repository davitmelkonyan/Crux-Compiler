package edu.uci.cs142a.crux.printing;

import edu.uci.cs142a.crux.frontend.ast.*;
import edu.uci.cs142a.crux.frontend.ast.traversal.DefaultNodeVisitor;

import java.io.PrintStream;
import java.util.stream.Collectors;

public final class ASTPrinter {
    private static final String indent = "  ";

    private final PrinterVisitor printer;
    private boolean typesEnabled = false;
    private int level = 0;

    public ASTPrinter(PrintStream stdout) {
        printer = new PrinterVisitor(stdout);
    }

    public void enableTypes() {
        typesEnabled = true;
    }

    public void print(Node node) {
        node.accept(printer);

        ++level;
        for (var child : node.getChildren()) {
            print(child);
        }
        --level;
    }

    private final class PrinterVisitor extends DefaultNodeVisitor {
        private final PrintStream stdout;

        private PrinterVisitor(PrintStream stdout) {
            this.stdout = stdout;
        }

        private void print(Node node, String extra) {
            var name = "ast." + node.getClass().getSimpleName();
            var position = node.getPosition();
            stdout.printf("%s%s%s%s%n", indent.repeat(level), name, position, extra);
        }

        @Override
        protected void visitDefault(Node node) {
            print(node, "");
        }

        @Override
        public void visit(AddressOf addressOf) {
            var extra = String.format("[%s]", addressOf.getSymbol().getName());
            print(addressOf, extra);
        }

        @Override
        public void visit(ArrayDeclaration arrayDeclaration) {
            var extra = String.format("[%s]", arrayDeclaration.getSymbol().toString(typesEnabled));
            print(arrayDeclaration, extra);
        }

        @Override
        public void visit(Call call) {
            var extra = String.format("[Symbol(%s)]", call.getCallee().getName());
            print(call, extra);
        }

        @Override
        public void visit(Comparison comparison) {
            var extra = String.format("[%s]", Comparison.Mnemonics[comparison.getOp()]);
            print(comparison, extra);
        }

        @Override
        public void visit(FunctionDefinition functionDefinition) {
            var parameterExtra = functionDefinition.getParameters().stream()
                    .map(x -> x.toString(typesEnabled))
                    .collect(Collectors.joining(", "));
            var extra = String.format("[%s, [%s]]", functionDefinition.getSymbol().toString(typesEnabled), parameterExtra);
            print(functionDefinition, extra);
        }

        @Override
        public void visit(LiteralBool literalBool) {
            var valueString = literalBool.getValue() ? "TRUE" : "FALSE";
            var extra = String.format("[%s]", valueString);
            print(literalBool, extra);
        }

        @Override
        public void visit(LiteralInt literalInt) {
            var extra = String.format("[%d]", literalInt.getValue());
            print(literalInt, extra);
        }

        @Override
        public void visit(VariableDeclaration variableDeclaration) {
            var extra = String.format("[%s]", variableDeclaration.getSymbol().toString(typesEnabled));
            print(variableDeclaration, extra);
        }
    }
}
