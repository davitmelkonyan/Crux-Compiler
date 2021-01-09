package edu.uci.cs142a.crux.midend;

import edu.uci.cs142a.crux.frontend.Symbol;
import edu.uci.cs142a.crux.frontend.ast.*;
import edu.uci.cs142a.crux.frontend.ast.traversal.NodeVisitor;
import edu.uci.cs142a.crux.frontend.pt.CruxParser;
import edu.uci.cs142a.crux.frontend.types.*;
import edu.uci.cs142a.crux.midend.ir.core.*;
import edu.uci.cs142a.crux.midend.ir.core.insts.*;
import edu.uci.cs142a.crux.midend.ir.types.*;
import edu.uci.cs142a.crux.midend.ir.types.Type;

import java.util.*;

/**
 * Lower from AST to IR
 * */
public final class ASTLower implements NodeVisitor {
    private Program mCurrentProgram = null;
    private Function mCurrentFunction = null;

    private Map<Symbol, AddressVar> mCurrentGlobalSymMap = null;
    private Map<Symbol, Variable> mCurrentLocalVarMap = null;
    private Map<String, AddressVar> mBuiltInFuncMap = null;

    /**
     * A scratch pad for visiting expressions
     * */
    private Value mExpressionValue = null;

    private Instruction mLastControlInstruction = null;
    private boolean fromAssignment = false;
    private boolean fromCall = false;
    private VariableDeclaration locVarDec = null;
    private int indexCount =-1;

    public Program lower(DeclarationList ast) {
        visit(ast);
        return mCurrentProgram;
    }

    /**
     * The top level Program
     * */
    @Override
    public void visit(DeclarationList declarationList) {
        mCurrentProgram = new Program();
        mCurrentGlobalSymMap = new LinkedHashMap<Symbol, AddressVar>();
        mBuiltInFuncMap = new HashMap<String, AddressVar>();
        vals = new HashMap<String, Integer>();
        for(Node d: declarationList.getChildren()){
            d.accept(this);
            mLastControlInstruction = null;
        }
    }

    /**
     * Function
     * */
    @Override
    public void visit(FunctionDefinition functionDefinition) {
        //mCurrentFunction = new Function();
        mCurrentLocalVarMap = new HashMap<Symbol, Variable>();
        String funcName = functionDefinition.getSymbol().getName();
        Type retType=null;
        //System.out.println(((FuncType)functionDefinition.getSymbol().getType()).getRet().getClass());
        if(((FuncType)functionDefinition.getSymbol().getType()).getRet().getClass()== IntType.class) {
            retType = IntegerTy.get(mCurrentProgram);
        } else if(((FuncType)functionDefinition.getSymbol().getType()).getRet().getClass()== BoolType.class){
            retType = BooleanTy.get(mCurrentProgram);
        }else if(((FuncType)functionDefinition.getSymbol().getType()).getRet().getClass()== VoidType.class){
            retType = VoidTy.get(mCurrentProgram);
        }else{
            throw new RuntimeException("Wrong return type");
        }

        List<LocalVar> paramList = new ArrayList<LocalVar>();
        List<Symbol> argList = functionDefinition.getParameters();
        List<Type> tyList = new ArrayList<Type>();
        for(int i=0; i< functionDefinition.getParameters().size();++i){
            //System.out.println("Param:"+functionDefinition.getParameters().get(0).toString());
            LocalVar l;
            if(argList.get(i).getType().getClass() == BoolType.class){
                l = new LocalVar(BooleanTy.get(mCurrentProgram),argList.get(i).getName());
            }else if(argList.get(i).getType().getClass() == IntType.class){
                l = new LocalVar(IntegerTy.get(mCurrentProgram),argList.get(i).getName());
            }else{
                //l=null;//FIX this
                l = new LocalVar(IntegerTy.get(mCurrentProgram),argList.get(i).getName());
            }
            mCurrentLocalVarMap.put(functionDefinition.getParameters().get(i),l);
            paramList.add(l);
        }
        Function f = new Function(funcName,paramList,retType);
        mCurrentProgram.addFunction(f);
        mCurrentFunction = f;
        for(Node st: functionDefinition.getStatements().getChildren()){
                System.out.print("\nNODE:" );
                System.out.println(st.toString());
            st.accept(this);
        }

        AddressVar addVar = mCurrentFunction.getTempAddressVar(retType);
        mCurrentGlobalSymMap.put(functionDefinition.getSymbol(),addVar);
        mLastControlInstruction = null;
        mExpressionValue = null;
        indexCount = -1;
        mCurrentLocalVarMap.clear();
        mCurrentFunction = null;
    }

    @Override
    public void visit(StatementList statementList) {
        for (Node n: statementList.getChildren()) {
            System.out.println("\t\tStatment: "+n);
            n.accept(this);
        }
    }

    /**
     * Declarations
     * */
    @Override
    public void visit(VariableDeclaration variableDeclaration) {
        if(mCurrentFunction==null && locVarDec==null){

            if(variableDeclaration.getSymbol().getType().getClass()!=IntType.class && variableDeclaration.getSymbol().getType().getClass()!=BoolType.class){
                throw new RuntimeException("Wrong Var Type");
            }
            Type varTy = variableDeclaration.getSymbol().getType().getClass()==IntType.class ? IntegerTy.get(mCurrentProgram):BooleanTy.get(mCurrentProgram);
            AddressVar addVar = new AddressVar(varTy,variableDeclaration.getSymbol().getName());
            AllocateInst allocateInst = new AllocateInst(addVar,IntegerConstant.get(mCurrentProgram,1));
            //mCurrentFunction.addInst(mLastControlInstruction,allocateInst);
            mCurrentProgram.addGlobalVar(allocateInst);
            mLastControlInstruction = allocateInst;
            mCurrentGlobalSymMap.put(variableDeclaration.getSymbol(),addVar);
        }else if(mCurrentFunction!=null && locVarDec!=null){
            Type varTy = variableDeclaration.getSymbol().getType().getClass()==IntType.class ? IntegerTy.get(mCurrentProgram):BooleanTy.get(mCurrentProgram);
            LocalVar addVar = new LocalVar(varTy,variableDeclaration.getSymbol().getName());
            mCurrentLocalVarMap.put(variableDeclaration.getSymbol(),addVar);
        }
        else{
            locVarDec = variableDeclaration;
        }

    }
    private int outerSize;
    @Override
    public void visit(ArrayDeclaration arrayDeclaration) {
        //System.out.println("\nLZ"+((ArrayType)arrayDeclaration.getSymbol().getType()).getExtent());
        if(arrayDeclaration.getSymbol().getType().getClass()!= ArrayType.class){
            throw new RuntimeException("Wrong Var Type");
        }
        AddressVar addVar;
        AllocateInst allocateInst;
        if((((ArrayType)arrayDeclaration.getSymbol().getType()).getBase()) instanceof ArrayType){
            //System.out.print("\nPLOR"+);
            //Type arrTy = ((ArrayType)arrayDeclaration.getSymbol().getType()).getBase().getClass()==IntType.class ? IntegerTy.get(mCurrentProgram):BooleanTy.get(mCurrentProgram);
            addVar = new AddressVar(IntegerTy.get(mCurrentProgram),arrayDeclaration.getSymbol().getName());
            int firstSize =((ArrayType)arrayDeclaration.getSymbol().getType()).getExtent();
            int secondSize = ((ArrayType)(((ArrayType)arrayDeclaration.getSymbol().getType()).getBase())).getExtent();
            outerSize = secondSize;
            allocateInst = new AllocateInst(addVar,IntegerConstant.get(mCurrentProgram,(firstSize) * (secondSize)));
            List<LocalVar> inds1 = new ArrayList<>();
            List<LocalVar> inds2 = new ArrayList<>();
            for(int i=0;i<firstSize;++i){
                LocalVar l = new LocalVar(IntegerTy.get(mCurrentProgram));
                inds1.add(l);
            }
            indicies.put(arrayDeclaration.getSymbol().getName(),inds1);
            indiciesRef.put(arrayDeclaration.getSymbol().getName(),inds1);
            for(int i=0;i<secondSize;++i){
                LocalVar l = new LocalVar(IntegerTy.get(mCurrentProgram));
                inds2.add(l);
            }
            indicies2D.put(arrayDeclaration.getSymbol().getName(),inds2);
            indiciesRef2D.put(arrayDeclaration.getSymbol().getName(),inds2);

        }else{
            Type arrTy = ((ArrayType)arrayDeclaration.getSymbol().getType()).getBase().getClass()==IntType.class ? IntegerTy.get(mCurrentProgram):BooleanTy.get(mCurrentProgram);
            addVar = new AddressVar(arrTy,arrayDeclaration.getSymbol().getName());
            allocateInst = new AllocateInst(addVar,IntegerConstant.get(mCurrentProgram,((ArrayType)arrayDeclaration.getSymbol().getType()).getExtent()));
            List<LocalVar> inds = new ArrayList<>();
            for(int i=0;i<((ArrayType)arrayDeclaration.getSymbol().getType()).getExtent();++i){
                LocalVar l = new LocalVar(IntegerTy.get(mCurrentProgram));
                inds.add(l);
            }
            indicies.put(arrayDeclaration.getSymbol().getName(),inds);
            indiciesRef.put(arrayDeclaration.getSymbol().getName(),inds);
        }
        mCurrentProgram.addGlobalVar(allocateInst);
        mLastControlInstruction = allocateInst;
        mCurrentGlobalSymMap.put(arrayDeclaration.getSymbol(),addVar);


    }

    /**
     * Binary Operators
     * */
    @Override
    public void visit(Addition addition) {
        fromBinaryOp = true;
        addition.getLeft().accept(this);
        var lhs = (LocalVar)mExpressionValue;
        addition.getRight().accept(this);
        var rhs = (LocalVar)mExpressionValue;
        fromBinaryOp = false;
        IntegerTy destTy = IntegerTy.get(mCurrentProgram);
        LocalVar dest = mCurrentFunction.getTempVar(destTy);
        BinaryAddInst binAdd = new BinaryAddInst(dest,lhs,rhs);
        mCurrentFunction.addInst(mLastControlInstruction,binAdd);
        mLastControlInstruction = binAdd;
        mExpressionValue = dest;

    }

    @Override
    public void visit(Subtraction subtraction) {
        fromBinaryOp = true;
        subtraction.getLeft().accept(this);
        var lhs = (LocalVar)mExpressionValue;
        subtraction.getRight().accept(this);
        var rhs = (LocalVar)mExpressionValue;
        fromBinaryOp = false;
        IntegerTy destTy = IntegerTy.get(mCurrentProgram);
        LocalVar dest = mCurrentFunction.getTempVar(destTy);
        BinarySubInst binSub = new BinarySubInst(dest,lhs,rhs);
        mCurrentFunction.addInst(mLastControlInstruction,binSub);
        mLastControlInstruction = binSub;
        mExpressionValue = dest;
    }

    @Override
    public void visit(Division division) {
        fromBinaryOp = true;
        division.getLeft().accept(this);
        var lhs = (LocalVar)mExpressionValue;
        division.getRight().accept(this);
        var rhs = (LocalVar)mExpressionValue;
        fromBinaryOp = false;
        IntegerTy destTy = IntegerTy.get(mCurrentProgram);
        LocalVar dest = mCurrentFunction.getTempVar(destTy);
        BinaryDivInst binDiv = new BinaryDivInst(dest,lhs,rhs);
        mCurrentFunction.addInst(mLastControlInstruction,binDiv);
        mLastControlInstruction = binDiv;
        mExpressionValue = dest;
    }

    @Override
    public void visit(Multiplication multiplication) {
        fromBinaryOp = true;
        multiplication.getLeft().accept(this);
        var lhs = (LocalVar)mExpressionValue;
        multiplication.getRight().accept(this);
        var rhs = (LocalVar)mExpressionValue;
        fromBinaryOp = false;
        IntegerTy destTy = IntegerTy.get(mCurrentProgram);
        LocalVar dest = mCurrentFunction.getTempVar(destTy);
        BinaryMulInst binMul = new BinaryMulInst(dest,lhs,rhs);
        mCurrentFunction.addInst(mLastControlInstruction,binMul);

        mLastControlInstruction = binMul;
        mExpressionValue = dest;
    }

    @Override
    public void visit(LogicalAnd logicalAnd) {
        LocalVar localVarLeft = mCurrentFunction.getTempVar(BooleanTy.get(mCurrentProgram));
        logicalAnd.getLeft().accept(this);
        boolean left = returns.get(((Call)logicalAnd.getLeft()).getCallee().getName());
        boolean right = returns.get(((Call)logicalAnd.getRight()).getCallee().getName());

        var lhs = (LocalVar)mExpressionValue;
                //System.out.println("LHS: "+lhs));

        CopyInst copyInst = new CopyInst(localVarLeft,lhs);
        mCurrentFunction.addInst(mLastControlInstruction,copyInst);
        mLastControlInstruction = copyInst;
        JumpInst jumpInst = new JumpInst(localVarLeft);
        mCurrentFunction.addInst(mLastControlInstruction,jumpInst);
        mLastControlInstruction = jumpInst;
        Instruction instruction = jumpInst;

        //if(true)
        if(left){
        logicalAnd.getRight().accept(this);}
        var rhs = (LocalVar)mExpressionValue;
        var rhsExpr = mLastControlInstruction;

        CopyInst copyInst1 = new CopyInst(localVarLeft,rhs);
        mCurrentFunction.addInst(mLastControlInstruction,copyInst1);
        mLastControlInstruction = copyInst1;
        mExpressionValue = localVarLeft;

//--print-ir test15.crx
// --emulator ./test19.crx --emulator-input test19.in
        NopInst n = new NopInst();
        mCurrentFunction.addInst(instruction,n);
        mCurrentFunction.addInst(mLastControlInstruction,n);
        mLastControlInstruction = n;

        Iterator<Instruction> iter = mCurrentFunction.getChildren(jumpInst);
        Instruction firstChild = iter.next();

        Instruction secondChild = iter.next();

        mCurrentFunction.updateControlEdge(instruction,secondChild, Instruction.ControlEdgeKind.Jump);
        //if(left && !right){
        //    mCurrentFunction.updateControlEdge(instruction,firstChild, Instruction.ControlEdgeKind.Jump);
        //}
        if(left){
            mCurrentFunction.updateControlEdge(instruction,rhsExpr, Instruction.ControlEdgeKind.Jump);
        }else{

        }

/*
        if(left && right){
            System.out.println("\nTT");
            System.out.println("1st: "+firstChild);
            System.out.println("2nd: "+secondChild);
            mCurrentFunction.updateControlEdge(instruction,secondChild, Instruction.ControlEdgeKind.Jump);
        }else if((!left && right)){
            System.out.println("\nF T");
            System.out.println("1st: "+firstChild);
            System.out.println("2nd: "+secondChild);
            mCurrentFunction.updateControlEdge(instruction,secondChild, Instruction.ControlEdgeKind.Jump);
        }else if(left && !right) {
            System.out.println("\nT F");
            System.out.println("1st: "+firstChild);
            System.out.println("2nd: "+secondChild);
            mCurrentFunction.updateControlEdge(instruction, firstChild, Instruction.ControlEdgeKind.Jump);
        }else {
            System.out.println("\nF F");
            System.out.println("1st: "+firstChild);
            System.out.println("2nd: "+secondChild);
            mCurrentFunction.updateControlEdge(instruction,firstChild, Instruction.ControlEdgeKind.Jump);
        }*/

        //System.out.println("\nBefore Then:"+mLastControlInstruction);
    }

    @Override
    public void visit(LogicalOr logicalOr) {//FIX OR
        LocalVar localVarLeft = mCurrentFunction.getTempVar(BooleanTy.get(mCurrentProgram));
        logicalOr.getLeft().accept(this);
        var lhs = (LocalVar)mExpressionValue;
        CopyInst copyInst = new CopyInst(localVarLeft,lhs);
        mCurrentFunction.addInst(mLastControlInstruction,copyInst);
        mLastControlInstruction = copyInst;
        JumpInst jumpInst = new JumpInst(localVarLeft);
        mCurrentFunction.addInst(mLastControlInstruction,jumpInst);
        mLastControlInstruction = jumpInst;
        Instruction instruction = jumpInst;
        logicalOr.getRight().accept(this);
        var rhs = (LocalVar)mExpressionValue;
        CopyInst copyInst1 = new CopyInst(localVarLeft,rhs);
        mCurrentFunction.addInst(mLastControlInstruction,copyInst1);
        mLastControlInstruction = copyInst1;
        mExpressionValue = localVarLeft;
        NopInst n = new NopInst();
        mCurrentFunction.addInst(instruction,n);
        mCurrentFunction.addInst(mLastControlInstruction,n);
        mLastControlInstruction = n;
        Iterator<Instruction> iter = mCurrentFunction.getChildren(jumpInst);
        Instruction nop = iter.next();
        Instruction inst = iter.next();
        mCurrentFunction.updateControlEdge(instruction,inst, Instruction.ControlEdgeKind.Jump);
    }

    private boolean global = false;
    @Override
    public void visit(AddressOf addressOf) {
        System.out.println("\t\t\tLocal Map: "+mCurrentLocalVarMap);
        System.out.println("\t\t\tAddressOf: "+addressOf.getSymbol());
        if(mCurrentLocalVarMap.containsKey(addressOf.getSymbol())) {
            if(mCurrentGlobalSymMap.containsKey(addressOf.getSymbol())){
                global = true;
            }
            System.out.println("\t\t\tFoundInLocal");
            mExpressionValue = mCurrentLocalVarMap.get(addressOf.getSymbol());

        }else if(mCurrentGlobalSymMap.containsKey(addressOf.getSymbol())){
            System.out.println("\t\t\tFoundInGlobal");
            global = true;
            mExpressionValue = mCurrentGlobalSymMap.get(addressOf.getSymbol());
            AddressVar dest = mCurrentFunction.getTempAddressVar(IntegerTy.get(mCurrentProgram));
            AddressAt addressAt;
            if(fromIndex){
                if(fromCall){
                    addressAt = new AddressAt(dest,(AddressVar)mExpressionValue,indiciesRef.get(addressOf.getSymbol().getName()).get(indexCount));
                }else{
                    //if(indicies.get(addressOf.getSymbol().getName()).size()>5){
                    //    System.out.println("P1"+indicies.get(addressOf.getSymbol().getName()));
                    //    System.out.println("P"+indicies.get(addressOf.getSymbol().getName()).get(4).getName());
                    //}
                    addressAt = new AddressAt(dest,(AddressVar)mExpressionValue,indicies.get(addressOf.getSymbol().getName()).get(indexCount));
                }
                mExpressionValue = dest;
            }else{
                addressAt = new AddressAt(dest,(AddressVar)mExpressionValue);
                mCurrentLocalVarMap.put(addressOf.getSymbol(),dest);
            }
            mCurrentFunction.addInst(mLastControlInstruction, addressAt);
            mLastControlInstruction = addressAt;
            System.out.println("\t\t\tmExpressionValue: "+mExpressionValue);
        } else{
            mExpressionValue = null;
        }



    }
    private Map<String, Integer> vals;
    private Map<String, Integer> localIndexes = new HashMap<>();
    private boolean assigningExpression = false;
    @Override
    public void visit(Assignment assignment) {
        if(assignment.getLocation() instanceof Index && assignment.getValue() instanceof LiteralInt){
            if(((Index)assignment.getLocation()).getOffset() instanceof LiteralInt) {
                vals.put(((AddressOf) ((Index) assignment.getLocation()).getBase()).getSymbol().getName(), ((LiteralInt) assignment.getValue()).getValue());
            }
        }
        System.out.println("\t\t Assignment Loc"+assignment.getLocation());
        System.out.println("\t\t Assignment Val"+assignment.getValue());
        fromAssignment = true; //only matters when value is call
        if(assignment.getValue() instanceof  Addition || assignment.getValue() instanceof Subtraction || assignment.getValue() instanceof Multiplication || assignment.getValue() instanceof Division){
            assigningExpression = true;
        }
        if(assignment.getValue() instanceof  LiteralInt && assignment.getLocation() instanceof AddressOf){
            localIndexes.put(((AddressOf)assignment.getLocation()).getSymbol().getName(),((LiteralInt)assignment.getValue()).getValue());
        }
        assignment.getValue().accept(this); //int,bool,array Type
        fromAssignment = false;
        var val = (LocalVar)mExpressionValue;
        assignment.getLocation().accept(this);
        /*Debug
        Map.Entry<Symbol,AddressVar> entry = mCurrentGlobalSymMap.entrySet().iterator().next();
        Symbol s = entry.getKey();
        AddressVar a = entry.getValue();
        System.out.println("GlobalMap: ");
        System.out.println(entry);
        System.out.println(s.getName());
        System.out.println(a.toString());
        System.out.println(assignment.getLocation());
        System.out.println(assignment.getValue());
        System.out.println(((AddressOf)assignment.getLocation()).getSymbol());*/
        if(mExpressionValue == null){
            visit(locVarDec);
            locVarDec = null;
            LocalVar loc;
            if(assignment.getLocation().getClass() == AddressOf.class){
                loc = (LocalVar)mCurrentLocalVarMap.get(((AddressOf)assignment.getLocation()).getSymbol());
            }else{
                loc = (LocalVar)mCurrentLocalVarMap.get(((Index)assignment.getLocation()));
            }
            CopyInst cpy = new CopyInst(loc,val);
            mCurrentFunction.addInst(mLastControlInstruction,cpy);
            mLastControlInstruction = cpy;
        }else{//found it
            Variable loc;
            if(mExpressionValue instanceof  LocalVar){
                if(assignment.getLocation().getClass() == AddressOf.class){
                    loc = (LocalVar)mCurrentLocalVarMap.get(((AddressOf)assignment.getLocation()).getSymbol());
                }else{
                    loc = (LocalVar)mCurrentLocalVarMap.get(((Index)assignment.getLocation()));
                }
                CopyInst cpy = new CopyInst((LocalVar)loc,val);
                mCurrentFunction.addInst(mLastControlInstruction,cpy);
                mLastControlInstruction = cpy;
            }else{//found in global space
                if(assignment.getLocation().getClass() == AddressOf.class){
                    if(assigningExpression){
                        AddressVar addressVar = mCurrentFunction.getTempAddressVar(IntegerTy.get(mCurrentProgram));
                        AddressAt addressAt = new AddressAt(addressVar,(AddressVar)mCurrentGlobalSymMap.get(((AddressOf)assignment.getLocation()).getSymbol()));
                        mCurrentFunction.addInst(mLastControlInstruction,addressAt);
                        mLastControlInstruction = addressAt;
                        loc = addressVar;
                    }else{
                        loc = (AddressVar)mCurrentLocalVarMap.get(((AddressOf)assignment.getLocation()).getSymbol());
                    }

                    StoreInst st = new StoreInst(val,(AddressVar)loc);
                    mCurrentFunction.addInst(mLastControlInstruction,st);
                    mLastControlInstruction = st;
                }else{/*
                    if(mCurrentLocalVarMap.containsKey(((AddressOf)(((Index)assignment.getLocation())).getBase()).getSymbol())){
                        loc = (AddressVar)mCurrentLocalVarMap.get(((AddressOf)(((Index)assignment.getLocation())).getBase()).getSymbol());
                    }else{
                        loc = (AddressVar)mCurrentGlobalSymMap.get(((AddressOf)(((Index)assignment.getLocation())).getBase()).getSymbol());
                    }*/
                    loc = (AddressVar)mExpressionValue;
                    StoreInst st = new StoreInst(val,(AddressVar)loc);
                    mCurrentFunction.addInst(mLastControlInstruction,st);
                    mLastControlInstruction = st;
                }
            }
        }
    }

    @Override
    public void visit(Call call) {
        List<LocalVar> args = new ArrayList<LocalVar>();
        for (Expression e : call.getArguments()) {
            System.out.println("CALL ARG: "+e.getClass());
            fromCall = true;
            e.accept(this);
            fromCall = false;
            var arg = (LocalVar) mExpressionValue;
            args.add(arg);
        }

        if(call.getCallee().getName()== "readInt" ||
           call.getCallee().getName()== "printInt" ||
           call.getCallee().getName()== "printBool" ||
           call.getCallee().getName()== "println"){

            AddressVar addressVar = null;
            //System.out.println(call.getCallee().getName());
            switch (call.getCallee().getName()) {
                case "readInt":
                    addressVar = new AddressVar(VoidTy.get(mCurrentProgram),"readInt");
                    mBuiltInFuncMap.put("readInt", addressVar);
                    break;
                case "printInt":
                    addressVar = new AddressVar(VoidTy.get(mCurrentProgram),"printInt");
                    mBuiltInFuncMap.put("printInt", addressVar);
                    break;
                case "printBool":
                    addressVar = new AddressVar(VoidTy.get(mCurrentProgram),"printBool");
                    mBuiltInFuncMap.put("printBool", addressVar);
                    break;
                case "println":
                    addressVar = new AddressVar(VoidTy.get(mCurrentProgram),"println");
                    mBuiltInFuncMap.put("println", addressVar);
                    break;
                default:
                    break;
            }
            CallInst callInst;
            if(fromAssignment || returningFunc){
                LocalVar dest = mCurrentFunction.getTempVar(VoidTy.get(mCurrentProgram));
                //System.out.println("\nERR: "+addressVar);
                //System.out.println("ERR2: "+args);
                callInst = new CallInst(dest,addressVar,args);
                mExpressionValue = dest;
                returningFunc = false;
            }else{
                 callInst = new CallInst(addressVar,args);
            }
            mCurrentFunction.addInst(mLastControlInstruction,callInst);
            mLastControlInstruction = callInst;
            //mExpressionValue = l;
        }else{
            System.out.print("pl="+((FuncType)call.getCallee().getType()).getRet().getClass()+"   ");
            if (((FuncType)call.getCallee().getType()).getRet().getClass() == BoolType.class) {
                BooleanTy calleeType = BooleanTy.get(mCurrentProgram);
                AddressVar addrVar = new AddressVar(calleeType,call.getCallee().getName());
                LocalVar dest = mCurrentFunction.getTempVar(calleeType);
                CallInst callInst = new CallInst(dest, addrVar, args);
                mCurrentFunction.addInst(mLastControlInstruction, callInst);
                mLastControlInstruction = callInst;
                mExpressionValue = dest;
            } else if (((FuncType)call.getCallee().getType()).getRet().getClass() == IntType.class) {
                IntegerTy calleeType = IntegerTy.get(mCurrentProgram);
                AddressVar addrVar = new AddressVar(calleeType,call.getCallee().getName());
                LocalVar dest = mCurrentFunction.getTempVar(calleeType);
                CallInst callInst = new CallInst(dest, addrVar, args);
                mCurrentFunction.addInst(mLastControlInstruction, callInst);
                mLastControlInstruction = callInst;
                mExpressionValue = dest;
            } else if (((FuncType)call.getCallee().getType()).getRet().getClass() == VoidType.class) {
                VoidTy calleeType = VoidTy.get(mCurrentProgram);
                AddressVar addrVar = new AddressVar(calleeType,call.getCallee().getName());
                CallInst callInst = new CallInst(addrVar, args);
                mCurrentFunction.addInst(mLastControlInstruction, callInst);
                mLastControlInstruction = callInst;
            }
            //System.out.println(mExpressionValue);
        }
    }

    private boolean fromComparison = false;
    @Override
    public void visit(Comparison comparison) {

        fromComparison = true;
        comparison.getLeft().accept(this);
        fromComparison = false;
        var lhs = (LocalVar)mExpressionValue;
        fromComparison = true;
        comparison.getRight().accept(this);
        fromComparison = false;
        var rhs = (LocalVar)mExpressionValue;


        BooleanTy destTy = BooleanTy.get(mCurrentProgram);
        LocalVar dest = mCurrentFunction.getTempVar(destTy);
        CompareInst.Predicate p;
        if(comparison.getOp()==0){  p = CompareInst.Predicate.GE; }
        else if(comparison.getOp()==1){ p = CompareInst.Predicate.LE; }
        else if(comparison.getOp()==2){ p = CompareInst.Predicate.NE; }
        else if(comparison.getOp()==3){ p = CompareInst.Predicate.EQ; }
        else if(comparison.getOp()==4){ p = CompareInst.Predicate.GT; }
        else { p = CompareInst.Predicate.LT; }


        CompareInst comp = new CompareInst(dest,p,lhs,rhs);
        mCurrentFunction.addInst(mLastControlInstruction,comp);
        mLastControlInstruction = comp;
        mExpressionValue = dest;
    }

    @Override
    public void visit(Dereference dereference) {
        //System.out.println("\t\tDereferance Address: "+ dereference.getAddress());
        dereference.getAddress().accept(this);
        LocalVar dest = mCurrentFunction.getTempVar(IntegerTy.get(mCurrentProgram));
        Instruction inst;

        //if(fromCall || fromReturn || fromComparison || fromIndex){
            if(global){
                Symbol s;
                if(dereference.getAddress() instanceof AddressOf){

                    s=((AddressOf)dereference.getAddress()).getSymbol();
                    //System.out.println("\t\tNew" + s);
                }else{
                    if(fromCall && indicies2D.size()!=0){
                        s = ((AddressOf)((Index)(((Index)dereference.getAddress()).getBase())).getBase()).getSymbol();
                        //s = ((AddressOf)(((Index)((Index)dereference.getAddress()).getBase()))).getSymbol();
                    }else{
                        s = ((AddressOf)(((Index)dereference.getAddress()).getBase())).getSymbol();
                    }

                }
                if(mExpressionValue == mCurrentLocalVarMap.get(s)){

                    //System.out.println("\t\tS: " + s);
                    //System.out.print("\t\tLco MAP" +mCurrentLocalVarMap);

                    if(!mCurrentGlobalSymMap.containsKey(s)){
                        //LocalVar addressVar = mCurrentFunction.getTempVar(IntegerTy.get(mCurrentProgram));
                        inst = new CopyInst(dest,mCurrentLocalVarMap.get(s));

                    }else{
                        AddressVar addressVar = mCurrentFunction.getTempAddressVar(IntegerTy.get(mCurrentProgram));
                        AddressAt addressAt = new AddressAt(addressVar,(AddressVar)mCurrentGlobalSymMap.get(s));
                        if(fromWhile && !whileSet){
                            firstWhileExpr = addressAt;
                            whileSet = true;
                        }
                        mCurrentFunction.addInst(mLastControlInstruction, addressAt);
                        mLastControlInstruction = addressAt;
                        var addr =  mCurrentLocalVarMap.get(s);
                        inst = new LoadInst(dest,addressVar);
                        global = false;
                    }


                }else{
                    Variable addr;
                    if(dereference.getAddress() instanceof AddressOf){
                        if(mCurrentLocalVarMap.containsKey(((AddressOf)dereference.getAddress()).getSymbol())){
                            addr = mCurrentLocalVarMap.get(((AddressOf)dereference.getAddress()).getSymbol());
                        }else{
                            addr = mCurrentGlobalSymMap.get(((AddressOf)dereference.getAddress()).getSymbol());
                        }
                    }else{
                        addr = (AddressVar)mExpressionValue;
                        /*
                        if(mCurrentLocalVarMap.containsKey(((AddressOf)((Index)dereference.getAddress()).getBase()).getSymbol())){
                            addr = mCurrentLocalVarMap.get(((AddressOf)((Index)dereference.getAddress()).getBase()).getSymbol());
                        }else{
                            addr = mCurrentGlobalSymMap.get(((AddressOf)((Index)dereference.getAddress()).getBase()).getSymbol());
                        }*/
                    }
                    inst = new LoadInst(dest,(AddressVar)addr);
                    global = false;
                }
            }else{
                inst = new CopyInst(dest,mExpressionValue);
            }
        /*}else{
            var addr =  mCurrentGlobalSymMap.get(((AddressOf)dereference.getAddress()).getSymbol());
            inst = new AddressAt(dest,addr);
        }*/
        if(fromWhile && !whileSet){
            firstWhileExpr = inst;
            whileSet = true;
        }
        mCurrentFunction.addInst(mLastControlInstruction,inst);
        mLastControlInstruction = inst;
        mExpressionValue = dest;
    }

    private void visit(Expression expression) {
        expression.accept(this);//WRONG! gna call itself
    }

    @Override
    public void visit(ExpressionList expressionList) {
        for (Expression e:expressionList.getExpressions()) {
            e.accept(this);
        }
    }
    private List<CopyInst> copyInstr = new ArrayList<>();
    private boolean fromIndex = false;
    private Map<String,List<LocalVar>> indicies = new HashMap<>();
    private Map<String,List<LocalVar>>indiciesRef = new HashMap<>();
    private Map<String,List<LocalVar>> indicies2D = new HashMap<>();
    private Map<String,List<LocalVar>>indiciesRef2D = new HashMap<>();
    private int indexingIndex = -1;
    private Index insideInd;
    private boolean secondIndexDone = false;
    private LocalVar innerOffset;
    private LocalVar outerOffset;
    private int returning = 0;
    private List<Integer> arrayIncidicies = new ArrayList<>();
    @Override
    public void visit(Index index) {
        if(index.getOffset() instanceof Dereference ){
            if(((Dereference)index.getOffset()).getAddress() instanceof Index){
                indexingIndex = 0;
            }
        }
        if(indexingIndex == 0 && index.getOffset() instanceof LiteralInt){
            insideInd = index;
        }
        if(index.getBase() instanceof Index && returning==0){
            returning = 1;
        }else if(returning==1){

            returning = 2;
        }
        //System.out.println("\t\tINDEX Base: "+(index.getBase()));
        //System.out.println("\t\tINDEX Offset: "+index.getOffset());
        index.getOffset().accept(this);
        if(index.getOffset() instanceof LiteralInt){
            indexCount = ((LiteralInt)index.getOffset()).getValue();
            //System.out.println("\t\tp:" + indexCount);
        }else if(index.getOffset() instanceof  Addition || index.getOffset() instanceof  Subtraction || index.getOffset() instanceof  Multiplication || index.getOffset() instanceof  Subtraction){
            //System.out.println("\t\tOFFset   "+((IntegerConstant)(((CopyInst)(copyInstr.get(0))).getSrcValue())).getValue());
            if(index.getOffset() instanceof  Addition){
                indexCount = ((IntegerConstant)(((CopyInst)(copyInstr.get(0))).getSrcValue())).getValue()+
                                ((IntegerConstant)(((CopyInst)(copyInstr.get(1))).getSrcValue())).getValue();
            }
            else if(index.getOffset() instanceof  Subtraction){
                indexCount = ((IntegerConstant)(((CopyInst)(copyInstr.get(0))).getSrcValue())).getValue()-
                                ((IntegerConstant)(((CopyInst)(copyInstr.get(1))).getSrcValue())).getValue();
            }
            else if(index.getOffset() instanceof  Multiplication){
                indexCount = ((IntegerConstant)(((CopyInst)(copyInstr.get(0))).getSrcValue())).getValue()*
                                ((IntegerConstant)(((CopyInst)(copyInstr.get(1))).getSrcValue())).getValue();
            }
            else if(index.getOffset() instanceof  Division){
                indexCount = ((IntegerConstant)(((CopyInst)(copyInstr.get(0))).getSrcValue())).getValue()/
                                ((IntegerConstant)(((CopyInst)(copyInstr.get(1))).getSrcValue())).getValue();
            }
            copyInstr.clear();
        }else if(index.getOffset() instanceof Dereference){
            if(indexingIndex == 0){
                //System.out.println("\t\tDeref1" + vals);
                //System.out.println("\t\tDeref2" + insideInd.getBase());
                //System.out.println("Deref3" + vals.get(mCurrentGlobalSymMap.get(((AddressOf)(insideInd.getBase())).getSymbol()).toString().substring(1)));
                indexCount = vals.get(mCurrentGlobalSymMap.get(((AddressOf)(insideInd.getBase())).getSymbol()).toString().substring(1));
            }else if(indicies2D.size()!=0){
               //System.out.println("\t\tDerefff1"+mCurrentLocalVarMap.get(((AddressOf)((Dereference)index.getOffset()).getAddress()).getSymbol()).toString());
//                System.out.println("\t\tDerefff1"+((AddressOf)((Dereference)index.getOffset()).getAddress()).getSymbol());
                indexCount = localIndexes.get(mCurrentLocalVarMap.get(((AddressOf)((Dereference)index.getOffset()).getAddress()).getSymbol()).toString().substring(1));
                //indexCount = -2;
            }


        }
               // System.out.println("\t\tOFFset: "+index.getOffset());
               // System.out.println("\t\tindexCount:"+indexCount);
        var offset = (LocalVar)mExpressionValue;
        LocalVar lVar = mCurrentFunction.getTempVar(IntegerTy.get(mCurrentProgram));
        CopyInst cpy;
        if(indicies2D.size()!=0 && index.getBase() instanceof Index){
            //index.getOffset() instanceof Dereference
            if(index.getBase() instanceof Index){
                cpy = new CopyInst(lVar,IntegerConstant.get(mCurrentProgram,1));
            }else{
                cpy = new CopyInst(lVar,IntegerConstant.get(mCurrentProgram,outerSize));
            }

        }else{
             cpy = new CopyInst(lVar,IntegerConstant.get(mCurrentProgram,1));
        }

        mCurrentFunction.addInst(mLastControlInstruction,cpy);
        mLastControlInstruction = cpy;
        LocalVar dest = mCurrentFunction.getTempVar(IntegerTy.get(mCurrentProgram));
        BinaryMulInst binaryMulInst = new BinaryMulInst(dest,offset,lVar);
        mCurrentFunction.addInst(mLastControlInstruction,binaryMulInst);
        mLastControlInstruction = binaryMulInst;
        if(returning ==1){
            outerOffset = dest;
        }
        if(returning==2){
            innerOffset = dest;
            returning +=1;
        } else if(returning==3 && indicies2D.size()!=0){
            LocalVar dest2 = mCurrentFunction.getTempVar(IntegerTy.get(mCurrentProgram));
            BinaryAddInst binaryAddInst = new BinaryAddInst(dest2,innerOffset,outerOffset);
            mCurrentFunction.addInst(mLastControlInstruction,binaryAddInst);
            mLastControlInstruction = binaryAddInst;
        }

        List<LocalVar> tempInd;
        String key;
        if(index.getBase() instanceof  AddressOf)
        { key = ((AddressOf)(index.getBase())).getSymbol().getName();
        }else{ key = ((AddressOf)(((Index)index.getBase())).getBase()).getSymbol().getName(); }

        if(fromCall){
            tempInd = indiciesRef.get(key);
            tempInd.add(indexCount,dest);//indexCount-1
            indiciesRef.put(key,tempInd);
        }else{
            if(indicies2D.size() ==0 )
            {tempInd = indicies.get(key);
            tempInd.add(indexCount,dest);
            indicies.put(key,tempInd);}
            else{
                if(!secondIndexDone){
                    tempInd = indicies2D.get(key);
                    tempInd.add(indexCount,dest);
                    indicies2D.put(key,tempInd);
                    secondIndexDone = true;
                }else{
                    tempInd = indicies.get(key);
                    tempInd.add(indexCount,dest);
                    indicies.put(key,tempInd);
                    secondIndexDone = false;
                }
            }
        }
        fromIndex = true;
        index.getBase().accept(this);
        fromIndex = false;
    }

    @Override
    public void visit(LiteralBool literalBool) {
        BooleanConstant boolConst = BooleanConstant.get(mCurrentProgram,literalBool.getValue());
        BooleanTy t = BooleanTy.get(mCurrentProgram);
        //System.out.println(mCurrentFunction==null);
        LocalVar locVar = mCurrentFunction.getTempVar(t);
        CopyInst cpyInst = new CopyInst(locVar,boolConst);
        mCurrentFunction.addInst(mLastControlInstruction,cpyInst);
        mLastControlInstruction = cpyInst;
        mExpressionValue = locVar;
    }

    private boolean fromBinaryOp = false;
    @Override
    public void visit(LiteralInt literalInt) {
        IntegerConstant inConst = IntegerConstant.get(mCurrentProgram,literalInt.getValue());
        LocalVar locVar = mCurrentFunction.getTempVar(IntegerTy.get(mCurrentProgram));
        CopyInst cpyInst = new CopyInst(locVar,inConst);
        mCurrentFunction.addInst(mLastControlInstruction,cpyInst);
        mLastControlInstruction = cpyInst;
        if(fromBinaryOp){
            copyInstr.add((CopyInst)mLastControlInstruction);
        }
        mExpressionValue = locVar;

    }

    @Override
    public void visit(LogicalNot logicalNot) {
        logicalNot.getInner().accept(this);
        var inner = (LocalVar)mExpressionValue;
        IntegerTy destTy = IntegerTy.get(mCurrentProgram);
        LocalVar dest = mCurrentFunction.getTempVar(destTy);
        UnaryNotInst notInst = new UnaryNotInst(dest,inner);
        mCurrentFunction.addInst(mLastControlInstruction,notInst);
        mLastControlInstruction = notInst;
        mExpressionValue = dest;
    }
    private boolean returningFunc = false;
    private boolean fromReturn = false;
    private Map<String,Boolean> returns = new HashMap<>();
    @Override
    public void visit(Return ret) {
        System.out.print(ret.getValue().getClass());
        if(ret.getValue() instanceof Call){
            returningFunc = true;
        }
        fromReturn = true;
        ret.getValue().accept(this);
        fromReturn = false;
        if(ret.getValue() instanceof LiteralBool){
            returns.put(mCurrentFunction.getName(),((LiteralBool)ret.getValue()).getValue());
        }
        var val = (LocalVar)mExpressionValue;
        ReturnInst returnInst = new ReturnInst(val);
        mCurrentFunction.addInst(mLastControlInstruction,returnInst);
        mLastControlInstruction = returnInst;
        //mExpressionValue = val;
    }

    /**
     * Control Structures
     * */
    @Override
    public void visit(IfElseBranch ifElseBranch) {

//        System.out.print("Cond1:"+((LiteralBool)ifElseBranch.getCondition()).getValue());
        ifElseBranch.getCondition().accept(this);
        var cond = (LocalVar)mExpressionValue;
        System.out.println("\nBefore Then:"+mLastControlInstruction);
        JumpInst jumpInst = new JumpInst(cond);
        mCurrentFunction.addInst(mLastControlInstruction,jumpInst);
        mLastControlInstruction = jumpInst;
        Instruction instruction = jumpInst;
        //System.out.println("\nAZ: "+(mExpressionValue).print());
        //LocalVar c = mCurrentFunction.getTempVar(BooleanTy.get(mCurrentProgram));

        if(ifElseBranch.getCondition() instanceof LiteralBool){
            if(((LiteralBool)ifElseBranch.getCondition()).getValue()==true){
                //NopInst n = new NopInst();
                //mCurrentFunction.addInst(instruction,n);

                ifElseBranch.getThenBlock().accept(this);
                //mCurrentFunction.updateControlEdge(instruction,mCurrentFunction.getChild(jumpInst), Instruction.ControlEdgeKind.Jump);
                if(ifElseBranch.getElseBlock().getChildren().size()==0){
                    NopInst n = new NopInst();
                    mCurrentFunction.addInst(instruction,n);
                    mCurrentFunction.addInst(mLastControlInstruction,n);
                    mLastControlInstruction = n;
                    ifElseBranch.getElseBlock().accept(this);
                    //mCurrentFunction.addInst(mLastControlInstruction,n);
                    //mLastControlInstruction = n;
                    Iterator<Instruction> iter = mCurrentFunction.getChildren(jumpInst);
                    Instruction nop = iter.next();
                    Instruction inst = iter.next();
                    mCurrentFunction.updateControlEdge(instruction,inst, Instruction.ControlEdgeKind.Jump);
                }
            }
            else{
                //now mLastControlInstruction is JUMP
                ifElseBranch.getThenBlock().accept(this);
                System.out.println("\n11: "+mLastControlInstruction);
                mCurrentFunction.updateControlEdge(instruction,mCurrentFunction.getChild(jumpInst), Instruction.ControlEdgeKind.Jump);

                if(ifElseBranch.getElseBlock().getChildren().size()==0){
                    NopInst n = new NopInst();
                    mCurrentFunction.addInst(instruction,n);
                    mCurrentFunction.addInst(mLastControlInstruction,n);
                    mLastControlInstruction = n;
                }else{
                    Instruction inst2 = mLastControlInstruction;
                    mLastControlInstruction = instruction;
                    //NOW mLastControlInstruction is JUMP again
                    ifElseBranch.getElseBlock().accept(this);
                    //NOW mLastControlInstruction is CALL

                    NopInst n = new NopInst();
                    mCurrentFunction.addInst(mLastControlInstruction,n);
                    mCurrentFunction.addInst(inst2,n);
                    mLastControlInstruction = n;
                }
            }
        }else {
            ifElseBranch.getThenBlock().accept(this);
            //mCurrentFunction.updateControlEdge(instruction,mCurrentFunction.getChild(jumpInst), Instruction.ControlEdgeKind.Jump);
            if (ifElseBranch.getElseBlock().getChildren().size() == 0) {
                NopInst n = new NopInst();
                mCurrentFunction.addInst(instruction, n);
                mCurrentFunction.addInst(mLastControlInstruction, n);
                mLastControlInstruction = n;
                Iterator<Instruction> iter = mCurrentFunction.getChildren(jumpInst);
                Instruction nop = iter.next();
                Instruction inst = iter.next();
                mCurrentFunction.updateControlEdge(instruction, inst, Instruction.ControlEdgeKind.Jump);
            } else {
                Instruction inst2 = mLastControlInstruction;
                mLastControlInstruction = instruction;
                //NOW mLastControlInstruction is JUMP again
                ifElseBranch.getElseBlock().accept(this);
                //NOW mLastControlInstruction is CALL
                NopInst n = new NopInst();
                mCurrentFunction.addInst(mLastControlInstruction, n);
                mCurrentFunction.addInst(inst2, n);
                mLastControlInstruction = n;
            }
        } //System.out.println("\n11: "+mLastControlInstruction);
    }

    private Instruction firstWhileExpr;
    private boolean fromWhile = false;
    private boolean whileSet = false;
    @Override
    public void visit(WhileLoop whileLoop) {
        fromWhile = true;
        whileLoop.getCondition().accept(this);
        fromWhile = false;
        var cond = (LocalVar)mExpressionValue;
        System.out.println("\t\tmLastControlInstruction before body:"+mLastControlInstruction);
        JumpInst jumpInst = new JumpInst(cond);
        mCurrentFunction.addInst(mLastControlInstruction,jumpInst);
        mLastControlInstruction = jumpInst;
        Instruction instruction = jumpInst;
        whileLoop.getBody().accept(this);
        mCurrentFunction.addInst(mLastControlInstruction,firstWhileExpr);
        System.out.println("\\t\tmLastControlInstruction before body:"+mLastControlInstruction);
        NopInst n = new NopInst();
        mCurrentFunction.addInst(instruction,n);
        //mCurrentFunction.addInst(mLastControlInstruction,n);
        mLastControlInstruction = n;

        Iterator<Instruction> iter = mCurrentFunction.getChildren(jumpInst);
        Instruction nop = iter.next();
        System.out.println("\nNOP:"+nop);
        Instruction inst = iter.next();
        System.out.println("\nNOPik:"+inst);
        mCurrentFunction.updateControlEdge(instruction,inst, Instruction.ControlEdgeKind.Jump);
    }
}
