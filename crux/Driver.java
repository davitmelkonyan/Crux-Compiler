package edu.uci.cs142a.crux;

import edu.uci.cs142a.crux.frontend.ANTLRErrorRecorder;
import edu.uci.cs142a.crux.frontend.ParseTreeLower;
import edu.uci.cs142a.crux.frontend.ast.DeclarationList;
import edu.uci.cs142a.crux.frontend.pt.CruxLexer;
import edu.uci.cs142a.crux.frontend.pt.CruxParser;
import edu.uci.cs142a.crux.frontend.types.TypeChecker;
import edu.uci.cs142a.crux.midend.ASTLower;
import edu.uci.cs142a.crux.printing.IRPrinter;
import edu.uci.cs142a.crux.midend.ir.core.Program;
import edu.uci.cs142a.crux.midend.ir.core.Emulator;
import edu.uci.cs142a.crux.backend.CodeGen;
import edu.uci.cs142a.crux.printing.ASTPrinter;
import edu.uci.cs142a.crux.printing.ParseTreePrinter;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.*;
import java.util.function.Supplier;

enum State {
    Continue,
    Finished,
    Error;

    State then(Supplier<State> nextStage) {
        if (this == Continue)
            return nextStage.get();
        return this;
    }

    State complete() {
        return this == Finished ? Finished : Error;
    }
}

/**
 * The driver that runs the various stages of the compilation.
 * <p>
 * DO NOT MODIFY ANY SIGNATURES OF PUBLIC FUNCTION IN THIS CLASS OR WE CAN'T GRADE YOUR PROJECT!
 */
public final class Driver {
    private final PrintStream out;
    private final PrintStream err;

    private boolean printPt = false;
    private boolean printAst = false;
    private boolean includeTypes = false;
    private boolean typeCheck = false;
    private boolean printIR = false;
    private boolean runEmulator = false;

    private String inputFile;
    private InputStream inputStream;
    private String emulatorInputFile = null;
    private InputStream emulatorInputStream = null;

    private CruxParser.ProgramContext parseTree;
    private DeclarationList ast;
    private Program irProgram;

    Driver() {
        this(System.out, System.err);
    }

    public Driver(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public void enablePrintParseTree() {
        printPt = true;
    }

    public void enablePrintAst() {
        printAst = true;
    }

    public void enableIncludeTypes() {
        includeTypes = true;
    }

    public void enableTypeCheck() {
        typeCheck = true;
    }

    public void enablePrintIR() { printIR = true; }

    public void enableEmulator() {
        runEmulator = true;
    }

    public void enableDebugEmulator() {
        runEmulator = true;
        Emulator.DEBUG = true;
    }

    public boolean hasInputFile() {
        return inputFile != null;
    }

    public String getInputFile() {
        return inputFile;
    }

    public void setInputFile(String inputFile) {
        this.inputFile = inputFile;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void setEmulatorInputFile(String inputFile) {
        emulatorInputFile = inputFile;
    }

    public void setEmulatorInput(InputStream inputStream) {
        emulatorInputStream = inputStream;
    }

    public boolean hasEmulatorInputFile() { return emulatorInputFile != null; }

    public State run() {
        return parse()
                .then(this::makeAST)
                .then(this::typeCheck)
                .then(this::emitIR)
                .then(this::emulator)
                .then(this::emitASM)
                .complete();
    }

    private State parse() {
        var input = openInput();
        var lexer = new CruxLexer(input);
        var tokenStream = new CommonTokenStream(lexer);

        var parser = new CruxParser(tokenStream);
        parser.removeErrorListeners();
        var errorRecorder = new ANTLRErrorRecorder();
        parser.addErrorListener(errorRecorder);

        parseTree = parser.program();

        if (errorRecorder.getErrorMessages().size() > 0) {
            for (var message : errorRecorder.getErrorMessages()) {
                err.println(message);
            }
            return State.Error;
        }

        if (printPt) {
            var printer = new ParseTreePrinter(out);
            printer.print(parseTree);
            return State.Finished;
        }

        return State.Continue;
    }

    private State makeAST() {
        var parseTreeLower = new ParseTreeLower(err);
        ast = parseTreeLower.lower(parseTree);

        if (parseTreeLower.hasEncounteredError()) {
            return State.Error;
        }

        if (printAst) {
            var astPrinter = new ASTPrinter(out);
            if (includeTypes)
                astPrinter.enableTypes();

            astPrinter.print(ast);
            return State.Finished;
        }

        return State.Continue;
    }

    private State typeCheck() {
        var typeChecker = new TypeChecker();
        typeChecker.check(ast);

        if (typeCheck) {
            if (typeChecker.getErrors().isEmpty()) {
                out.println("Crux Program has no type errors.");
            } else {
                out.println("Error type-checking file.");
            }
        }

        var errors = typeCheck ? out : err;
        if (!typeChecker.getErrors().isEmpty()) {
            for (var error : typeChecker.getErrors()) {
                errors.println(error);
            }
            return State.Error;
        }

        return typeCheck ? State.Finished : State.Continue;
    }

    private State emitIR() {
        var astLower = new ASTLower();
        irProgram = astLower.lower(ast);

        if(printIR) {
            var printer = new IRPrinter(out);
            printer.print(irProgram);
            return State.Finished;
        }

        return State.Continue;
    }

    private State emitASM() {
        var codegen = new CodeGen(irProgram);
        codegen.genCode();

        return State.Finished;
    }

    private State emulator() {
        if (runEmulator) {
            var emulatorInput = openEmulatorInput();
            var emulator = new Emulator(irProgram, emulatorInput, out);
            emulator.run();
            return State.Finished;
        }

        return State.Continue;
    }

    private CharStream openInput() {
        if (inputStream == null) {
            try {
                return CharStreams.fromFileName(inputFile);
            } catch (IOException e) {
                throw new RuntimeException(String.format("cannot read file '%s'", inputFile), e);
            }
        } else {
            try {
                return CharStreams.fromStream(inputStream);
            } catch (IOException e) {
                throw new RuntimeException("cannot read from input stream.");
            }
        }
    }

    private InputStream openEmulatorInput() {
        if(emulatorInputFile != null) {
            try {
                return new FileInputStream(emulatorInputFile);
            } catch (IOException e) {
                throw new RuntimeException(String.format("cannot read file '%s'", emulatorInputFile), e);
            }
        } else if(emulatorInputStream != null) {
            return emulatorInputStream;
        } else {
            return System.in;
        }
    }
}
