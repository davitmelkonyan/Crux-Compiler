package edu.uci.cs142a.crux.midend.ir.core;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import edu.uci.cs142a.crux.midend.ir.Formattable;
import edu.uci.cs142a.crux.midend.ir.types.FunctionTy;
import edu.uci.cs142a.crux.midend.ir.types.Type;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A function which is the lowered version of a {@link edu.uci.cs142a.crux.frontend.ast.FunctionDefinition}. The
 * difference to the AST version is, that the body of the function does not consist of a list of statements, but instead
 * it is a graph, in which instructions are nodes and the control flow are the edges.
 */
@SuppressWarnings("UnstableApiUsage")
public final class Function implements Formattable {
    private String mFuncName;
    private List<LocalVar> mArgs;
    private FunctionTy mFuncType;

    private static final int FUNC_FORMAT_INDENT = 2;
    private MutableValueGraph<Instruction, Instruction.ControlEdgeKind> mInstGraph;

    private int mTempVarCounter, mTempAddressVarCounter;
    private Instruction startInstruction;

    public Function(String name, List<LocalVar> args, Type returnType) {
        mFuncName = name;
        mArgs = List.copyOf(args);
        mFuncType = FunctionTy.get(returnType, args.stream().map(Value::getType).collect(Collectors.toList()));
        mTempVarCounter = 0;
        mTempAddressVarCounter = 0;
        mInstGraph = ValueGraphBuilder.directed().build();
    }

    List<LocalVar> getArguments() {
        return List.copyOf(mArgs);
    }
  
    public String getName() { return mFuncName; }

    public FunctionTy getFuncType() { return mFuncType; }

    public LocalVar getTempVar(Type type) {
        var name = String.format("t%d", mTempVarCounter++);
        return new LocalVar(type, name);
    }
    public AddressVar getTempAddressVar(Type type) {
        var name = String.format("t%d", mTempAddressVarCounter++);
        return new AddressVar(type, name);
    }

    public Instruction getStart() {
        return startInstruction;
    }
  
    public void addInst(Instruction from, Instruction to, Instruction.ControlEdgeKind ctrlEdge) {
        if (from == null)
            startInstruction = to;
        // Create nodes if not exist
        if(from != null && !mInstGraph.nodes().contains(from)) {
            mInstGraph.addNode(from);
        }
        if(!mInstGraph.nodes().contains(to)) {
            mInstGraph.addNode(to);
        }

        // Add edge between them
        if(from != null && mInstGraph.hasEdgeConnecting(from, to)) {
           throw new AssertionError("Edge already exist");
        }
        if(from != null) {
            mInstGraph.putEdgeValue(from, to, ctrlEdge);
        }
    }
    public void addInst(Instruction from, Instruction to) {
        addInst(from, to, Instruction.ControlEdgeKind.Continue);
    }

    public void updateControlEdge(Instruction from, Instruction to, Instruction.ControlEdgeKind newCtrlEdge) {
        if(!mInstGraph.hasEdgeConnecting(from, to)) {
            throw new AssertionError("No edge connected");
        }
        mInstGraph.putEdgeValue(from, to, newCtrlEdge);
    }

    public boolean isJump(Instruction src, Instruction dst) {
        return mInstGraph.edgeValue(src, dst).get() == Instruction.ControlEdgeKind.Jump;
    }
  
    public Iterator<Instruction> getChildren(Instruction parent) {
        return mInstGraph.successors(parent).iterator();
    }
    // If it has only one child, throw exception if has multiple
    public Instruction getChild(Instruction parent) {
        var children = mInstGraph.successors(parent);
        if(children.size() > 1) {
            throw new AssertionError("More than one child or empty!");
        } else if (children.size() == 0) {
            return null;
        }
        return children.iterator().next();
    }

    @Override
    public String format(java.util.function.Function<Value, String> valueFormatter) {
        var funcName = getName();
        var funcDotBuilder = new StringBuilder();
        var indent = FUNC_FORMAT_INDENT;
        funcDotBuilder.append(" ".repeat(indent))
                .append("subgraph cluster_").append(funcName).append(" {\n");
        indent *= 2;

        // Styles
        funcDotBuilder.append(" ".repeat(indent)).append("style=filled;")
                .append("color=lightgrey;")
                .append("node [style=filled, color=white];")
                .append("\n");

        final var funcType = "function %%%s(%s) -> %s";
        var argStrStream = mArgs.stream().map(valueFormatter).collect(Collectors.toList());
        var argStr = String.join(",", argStrStream);
        var funcHeader = String.format(funcType, getName(), argStr, getFuncType().getReturnType().print());
        funcDotBuilder.append(" ".repeat(indent))
                .append(String.format("label=\"%s\";\n", funcHeader));

        // Print nodes
        int nodeCounter = 0;
        final var nodePrefix = funcName + "_n";
        Map<Instruction, String> nodeIdMap = new HashMap<>();
        // Only print edge labels for nodes that have multiple (out) edges
        Set<Instruction> multiEdgeNodes = new HashSet<>();
        for(var inst : mInstGraph.nodes()) {
            var nodeId = String.format("%s%d", nodePrefix, nodeCounter++);
            nodeIdMap.put(inst, nodeId);
            funcDotBuilder.append(" ".repeat(indent))
                            .append(nodeId)
                            .append(" [label=\"");
            funcDotBuilder.append(inst.format(valueFormatter))
                            .append("\"];\n");
            //System.out.println("OutDeg:"+mInstGraph.outDegree(inst));
            if(mInstGraph.outDegree(inst) > 1) {
                multiEdgeNodes.add(inst);
            }
        }

        // Print edges
        for(var edge : mInstGraph.edges()) {
            //System.out.println("Source:"+edge.source().getClass());
            //System.out.println("Target:"+edge.target().getClass());
            var source = edge.source();
            var dest = edge.target();
            if(!nodeIdMap.containsKey(source) || !nodeIdMap.containsKey(dest)) {
                throw new AssertionError("Node haven't put into the map?");
            }
            funcDotBuilder.append(" ".repeat(indent))
                    .append(nodeIdMap.get(source))
                    .append(" -> ")
                    .append(nodeIdMap.get(dest));

            if(multiEdgeNodes.contains(source)) {
                // Print label on this edge
                funcDotBuilder.append(" [label=\"  ");
                var edgeKind = mInstGraph.edgeValue(edge);
                if(edgeKind.isPresent()) {
                    switch (edgeKind.get()) {
                        case Jump:
                            funcDotBuilder.append("True");
                            break;
                        case Continue:
                            funcDotBuilder.append("False");
                            break;
                    }
                }
                funcDotBuilder.append("  \"]");
            }
            funcDotBuilder.append(";\n");
        }

        // End
        indent /= 2;
        funcDotBuilder.append(" ".repeat(indent))
                .append("}\n");

        return funcDotBuilder.toString();
    }
}
