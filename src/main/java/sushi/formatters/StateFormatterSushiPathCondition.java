package sushi.formatters;

import static jbse.apps.run.JAVA_MAP_Utils.isInitialMapField;
import static jbse.apps.run.JAVA_MAP_Utils.possiblyAdaptMapModelSymbols;
import static sushi.util.TypeUtils.javaClass;
import static sushi.util.TypeUtils.javaPrimitiveType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jbse.common.Type;
import jbse.common.exc.UnexpectedInternalException;
import jbse.mem.Clause;
import jbse.mem.ClauseAssume;
import jbse.mem.ClauseAssumeAliases;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.ClauseAssumeNull;
import jbse.mem.ClauseAssumeReferenceSymbolic;
import jbse.mem.Objekt;
import jbse.mem.State;
import jbse.mem.exc.FrozenStateException;
import jbse.rewr.CalculatorRewriting;
import jbse.val.Any;
import jbse.val.Calculator;
import jbse.val.Expression;
import jbse.val.NarrowingConversion;
import jbse.val.Operator;
import jbse.val.Primitive;
import jbse.val.PrimitiveSymbolicApply;
import jbse.val.PrimitiveSymbolicAtomic;
import jbse.val.PrimitiveVisitor;
import jbse.val.ReferenceSymbolic;
import jbse.val.ReferenceSymbolicApply;
import jbse.val.Simplex;
import jbse.val.Symbolic;
import jbse.val.Term;
import jbse.val.Value;
import jbse.val.WideningConversion;

/**
 * A {@link Formatter} used by SUSHI (check of path condition
 * clauses).
 * 
 * @author Pietro Braione
 */
public final class StateFormatterSushiPathCondition implements FormatterSushi {
    private final long methodNumber;
    private final Supplier<Long> traceCounterSupplier;
    private final Supplier<State> initialStateSupplier;
    private final boolean shallRelaxLastExpansionClause;
    private HashMap<Long, String> stringLiterals = null;
    private StringBuilder output = new StringBuilder();
    private int testCounter = 0;

    public StateFormatterSushiPathCondition(long methodNumber,
                                            Supplier<Long> traceCounterSupplier,
                                            Supplier<State> initialStateSupplier, 
                                            boolean shallRelaxLastExpansionClause) {
        this.methodNumber = methodNumber;
        this.traceCounterSupplier = traceCounterSupplier;
        this.initialStateSupplier = initialStateSupplier;
        this.shallRelaxLastExpansionClause = shallRelaxLastExpansionClause;
    }

    public StateFormatterSushiPathCondition(int methodNumber,
                                            Supplier<State> initialStateSupplier, 
                                            boolean shallRelaxLastExpansionClause) {
        this(methodNumber, null, initialStateSupplier, shallRelaxLastExpansionClause);
    }

    @Override
    public void setConstants(Map<Long, String> stringLiterals) {
        this.stringLiterals = new HashMap<>(stringLiterals); //safety copy
    }

    @Override
    public void formatPrologue() {
        try {
            //package declaration
            this.output.append("package ");
            final State initialState = this.initialStateSupplier.get();
            final String initialCurrentClassName = initialState.getStack().get(0).getMethodClass().getClassName();
            final String initialCurrentClassPackageName = initialCurrentClassName.substring(0, initialCurrentClassName.lastIndexOf('/')).replace('/', '.');
            this.output.append(initialCurrentClassPackageName);
            this.output.append(";\n\n");
            
            final long traceCounter = (this.traceCounterSupplier == null ? -1 : this.traceCounterSupplier.get());

            //imports and class declaration
            this.output.append(PROLOGUE_1);
            this.output.append('_');
            this.output.append(this.methodNumber);
            if (this.traceCounterSupplier != null) {
                this.output.append('_');
                this.output.append(traceCounter);
            }
            
            //constant declarations
            this.output.append(PROLOGUE_2);
            for (Map.Entry<Long, String> lit : this.stringLiterals.entrySet()) {
                this.output.append(INDENT_1);
                this.output.append("private static final String CONST_");
                this.output.append(lit.getKey());
                this.output.append(" = \"");
                this.output.append(lit.getValue());
                this.output.append("\";\n");
            }
            
            //private members and constructor declaration
            this.output.append(PROLOGUE_3);
            this.output.append('_');
            this.output.append(this.methodNumber);
            if (this.traceCounterSupplier != null) {
                this.output.append('_');
                this.output.append(traceCounter);
            }		
            this.output.append("(ClassLoader classLoader) {\n");
            this.output.append(INDENT_2);
            this.output.append("this.classLoader = classLoader;\n");
            for (long heapPos : new TreeSet<Long>(stringLiterals.keySet())) {
                this.output.append(INDENT_2);
                this.output.append("this.constants.put(");
                this.output.append(heapPos);
                this.output.append("L, CONST_");
                this.output.append(heapPos);
                this.output.append(");\n");
            }
            this.output.append(INDENT_1);
            this.output.append("}\n\n");
        } catch (FrozenStateException e) {
            this.output.delete(0, this.output.length());
        }
    }

    @Override
    public void formatState(State state) {
        try {
            new MethodUnderTest(this.output, this.initialStateSupplier.get(), state, this.testCounter, this.shallRelaxLastExpansionClause);
            this.output.append("}\n"); //closes the class declaration
        } catch (FrozenStateException e) {
            this.output.delete(0, this.output.length());
        }
        ++this.testCounter;
    }

    @Override
    public String emit() {
        return this.output.toString();
    }

    @Override
    public void cleanup() {
        this.output = new StringBuilder();
        this.testCounter = 0;
    }

    private static final String INDENT_1 = "    ";
    private static final String INDENT_2 = INDENT_1 + INDENT_1;
    private static final String INDENT_3 = INDENT_1 + INDENT_2;
    private static final String INDENT_4 = INDENT_1 + INDENT_3;
    private static final String PROLOGUE_1 =
    "import static " + sushi.compile.path_condition_distance.DistanceBySimilarityWithPathCondition.class.getName() + ".distance;\n" +
    "\n" +
    "import static java.lang.Double.*;\n" +
    "import static java.lang.Math.*;\n" +
    "\n" +
    "import " + sushi.compile.path_condition_distance.DistanceBySimilarityWithPathCondition.class.getPackage().getName() + ".*;\n" +
    "import " + sushi.logging.Level.class.getName() + ";\n" +
    "import " + sushi.logging.Logger.class.getName() + ";\n" +
    "\n" +
    "import java.util.ArrayList;\n" +
    "import java.util.HashMap;\n" +
    "import java.util.List;\n" +
    "\n" +
    "public class EvoSuiteWrapper";
    private static final String PROLOGUE_2 = " {\n" +
    INDENT_1 + "private static final double SMALL_DISTANCE = 1;\n" +
    INDENT_1 + "private static final double BIG_DISTANCE = 1E300;\n" +
    "\n";
    private static final String PROLOGUE_3 = "\n" +
    INDENT_1 + "private final HashMap<Long, String> constants = new HashMap<>();\n" +
    INDENT_1 + "private final ClassLoader classLoader;\n" +
    INDENT_1 + "private final SushiLibCache cache = new SushiLibCache();\n\n" +
    INDENT_1 + "public EvoSuiteWrapper";

    private static class MethodUnderTest {
        private final StringBuilder s;
        private final HashMap<Symbolic, String> symbolsToVariables = new HashMap<>();
        private final HashMap<String, Symbolic> variablesToSymbols = new HashMap<>();
        private final ArrayList<String> inputVariables = new ArrayList<>();
        private final Calculator calc = new CalculatorRewriting(); //dummy
        private boolean panic = false;

        MethodUnderTest(StringBuilder s, State initialState, State finalState, int testCounter, boolean shallRelaxLastExpansionClause) 
        throws FrozenStateException {
            this.s = s;
            appendMethodDeclaration(initialState, finalState, testCounter);
            appendPathCondition(finalState, testCounter, shallRelaxLastExpansionClause);
            appendCandidateObjects(finalState, testCounter);
            appendIfStatement(testCounter);
            appendMethodEnd(finalState, testCounter);
        }

        public static String javaType(Symbolic symbol) {
            if (symbol instanceof Primitive) { //either PrimitiveSymbolic or Term (however, it should never be the case of a Term)
                final char type = ((Primitive) symbol).getType();
                return javaPrimitiveType(type);
            } else if (symbol instanceof ReferenceSymbolic) {
                final String className = javaClass(((ReferenceSymbolic) symbol).getStaticType(), true);
                return className;
            } else {
                //this should never happen
                throw new RuntimeException("Reached unreachable branch while calculating the Java type of a symbol: Perhaps some type of symbol is not handled yet.");
            }
        }

        private void appendMethodDeclaration(State initialState, State finalState, int testCounter) {
            if (this.panic) {
                return;
            }

            final List<Symbolic> inputs;
            try {
                inputs = initialState.getStack().get(0).localVariables().values().stream()
                .filter(v -> v.getValue() instanceof Symbolic)
                .map(v -> (Symbolic) v.getValue())
                .collect(Collectors.toList());
            } catch (IndexOutOfBoundsException | FrozenStateException e) {
                throw new UnexpectedInternalException(e);
            }

            this.s.append(INDENT_1);
            this.s.append("public double test");
            this.s.append(testCounter);
            this.s.append("(");
            boolean firstDone = false;
            for (Symbolic symbol : inputs) {
                makeVariableFor(symbol);
                final String varName = getVariableFor(symbol);
                this.inputVariables.add(varName);
                if (firstDone) {
                    this.s.append(", ");
                } else {
                    firstDone = true;
                }
                final String type = javaType(symbol);
                this.s.append(type);
                this.s.append(' ');
                this.s.append(varName);
            }
            this.s.append(") throws Exception {\n");
            this.s.append(INDENT_2);
            this.s.append("//generated for state ");
            this.s.append(finalState.getBranchIdentifier());
            this.s.append('[');
            this.s.append(finalState.getSequenceNumber());
            this.s.append("]\n");
        }

        private void appendPathCondition(State finalState, int testCounter, boolean shallRelaxLastExpansionClause) 
        throws FrozenStateException {
            if (this.panic) {
                return;
            }
            this.s.append(INDENT_2);
            this.s.append("final ArrayList<ClauseSimilarityHandler> pathConditionHandler = new ArrayList<>();\n");
            this.s.append(INDENT_2);
            this.s.append("ValueCalculator valueCalculator;\n");
            final List<Clause> pathCondition = finalState.getPathCondition();
            final int pathConditionSize = pathCondition.size();
            int currentClause = 0;
            for (Clause clause : pathCondition) {
                ++currentClause;
                if (shouldSkip(clause)) {
                    continue;
                }
                if (clause instanceof ClauseAssumeExpands) {
                    this.s.append(INDENT_2);
                    this.s.append("// "); //comment
                    this.s.append(clause.toString());
                    this.s.append("\n");
                    final ClauseAssumeExpands clauseExpands = (ClauseAssumeExpands) clause;
                    final ReferenceSymbolic symbol = clauseExpands.getReference();
                    final long heapPosition = clauseExpands.getHeapPosition();
                    setWithNewObject(finalState, symbol, heapPosition, (shallRelaxLastExpansionClause && currentClause == pathConditionSize));
                } else if (clause instanceof ClauseAssumeNull) {
                    this.s.append(INDENT_2);
                    this.s.append("// "); //comment
                    this.s.append(clause.toString());
                    this.s.append("\n");
                    final ClauseAssumeNull clauseNull = (ClauseAssumeNull) clause;
                    final ReferenceSymbolic symbol = clauseNull.getReference();
                    setWithNull(symbol);
                } else if (clause instanceof ClauseAssumeAliases) {
                    this.s.append(INDENT_2);
                    this.s.append("// "); //comment
                    this.s.append(clause.toString());
                    this.s.append("\n");
                    final ClauseAssumeAliases clauseAliases = (ClauseAssumeAliases) clause;
                    final ReferenceSymbolic symbol = clauseAliases.getReference();
                    final long heapPosition = clauseAliases.getHeapPosition();
                    setWithAlias(finalState, symbol, heapPosition);
                } else if (clause instanceof ClauseAssume) {
                    this.s.append(INDENT_2);
                    this.s.append("// "); //comment
                    this.s.append(clause.toString());
                    this.s.append("\n");
                    final ClauseAssume clauseAssume = (ClauseAssume) clause;
                    final Primitive assumption = clauseAssume.getCondition();
                    setNumericAssumption(finalState, assumption);
                } //else, do nothing
            }
            this.s.append("\n");
        }

        private boolean shouldSkip(Clause clause) {
            if (clause instanceof ClauseAssumeReferenceSymbolic) {
            	final ReferenceSymbolic ref = ((ClauseAssumeReferenceSymbolic) clause).getReference(); 
                //exclude all the clauses with shape ClauseAssumeReferenceSymbolic
                //if they refer to the resolution of a symbolic reference that is a 
                //function application
                if (ref instanceof ReferenceSymbolicApply) {
                    return true;
                }

                //exclude all the clauses with shape ClauseAssumeReferenceSymbolic
                //if they refer to the resolution of the field initialMap of HashMap-models, since
                //initialMap is an internal field of the models and does not exist in concrete hashMaps
                if (isInitialMapField(ref)) {
                    return true;
                }
            }

            //all other clauses are accepted
            return false;
        }

        private void appendCandidateObjects(State finalState, int testCounter) {
            if (this.panic) {
                return;
            }
            this.s.append(INDENT_2);
            this.s.append("final HashMap<String, Object> candidateObjects = new HashMap<>();\n");
            for (String inputVariable : this.inputVariables) {
                this.s.append(INDENT_2);
                this.s.append("candidateObjects.put(\"");
                this.s.append(getPossiblyAdaptedOriginString(getSymbolFor(inputVariable)));
                this.s.append("\", ");
                this.s.append(inputVariable);
                this.s.append(");\n");
            }			
        }

        private void appendIfStatement(int testCounter) {
            this.s.append(INDENT_2);
            this.s.append("double d = distance(pathConditionHandler, candidateObjects, this.constants, this.classLoader, this.cache);\n");
            this.s.append(INDENT_2);
            this.s.append("if (d == 0.0d)\n");
            this.s.append(INDENT_3);
            this.s.append("System.out.println(\"test");
            this.s.append(testCounter);
            this.s.append(" 0 distance\");\n");
            this.s.append(INDENT_2);
            this.s.append("return d;\n");
        }

        private void appendMethodEnd(State finalState, int testCounter) {
            if (this.panic) {
                this.s.delete(0, s.length());
                this.s.append(INDENT_1);
                this.s.append("//Unable to generate test case ");
                this.s.append(testCounter);
                this.s.append(" for state ");
                this.s.append(finalState.getBranchIdentifier());
                this.s.append('[');
                this.s.append(finalState.getSequenceNumber());
                this.s.append("]\n");
            } else {
                this.s.append(INDENT_1);
                this.s.append("}\n");
            }
        }

        private void setWithNewObject(State finalState, ReferenceSymbolic symbol, long heapPosition, boolean relax) 
        throws FrozenStateException {
            final String expansionClass = javaClass(getTypeOfObjectInHeap(finalState, heapPosition), false);
            this.s.append(INDENT_2);
            this.s.append("pathConditionHandler.add(new SimilarityWithRefToFreshObject(\"");
            this.s.append(getPossiblyAdaptedOriginString(symbol));
            if (relax) {
                this.s.append("\"));\n");
            } else {
                this.s.append("\", Class.forName(\"");
                this.s.append(expansionClass); //TODO arrays
                this.s.append("\")));\n");
            }
        }

        private void setWithNull(ReferenceSymbolic symbol) {
            this.s.append(INDENT_2);
            this.s.append("pathConditionHandler.add(new SimilarityWithRefToNull(\"");
            this.s.append(getPossiblyAdaptedOriginString(symbol));
            this.s.append("\"));\n");
        }

        private void setWithAlias(State finalState, ReferenceSymbolic symbol, long heapPosition) {
            final String target = getOriginStringOfObjectInHeap(finalState, heapPosition);
            this.s.append(INDENT_2);
            this.s.append("pathConditionHandler.add(new SimilarityWithRefToAlias(\"");
            this.s.append(getPossiblyAdaptedOriginString(symbol));
            this.s.append("\", \"");
            this.s.append(target);
            this.s.append("\"));\n");
        }

        private int varCounter = 0;
        private String generateVarNameFromOrigin(String name) {
            return "V" + this.varCounter++;
        }

        private void makeVariableFor(Symbolic symbol) {
            if (!this.symbolsToVariables.containsKey(symbol)) {
                final String origin = getPossiblyAdaptedOriginString(symbol);
                final String varName = generateVarNameFromOrigin(origin);
                this.symbolsToVariables.put(symbol, varName);
                this.variablesToSymbols.put(varName, symbol);
            }
        }

        private String getVariableFor(Symbolic symbol) {
            return this.symbolsToVariables.get(symbol);
        }

        private Symbolic getSymbolFor(String varName) {
            return this.variablesToSymbols.get(varName);
        }

        private static String getTypeOfObjectInHeap(State finalState, long num) throws FrozenStateException {
            final Map<Long, Objekt> heap = finalState.getHeap();
            final Objekt o = heap.get(num);
            return o.getType().getClassName();
        }

        private String getOriginStringOfObjectInHeap(State finalState, long heapPos) {
            final Collection<Clause> path = finalState.getPathCondition();
            for (Clause clause : path) {
                if (clause instanceof ClauseAssumeExpands) {
                    final ClauseAssumeExpands clauseExpands = (ClauseAssumeExpands) clause;
                    final long heapPosCurrent = clauseExpands.getHeapPosition();
                    if (heapPosCurrent == heapPos) {
                        return getPossiblyAdaptedOriginString(clauseExpands.getReference());
                    }
                }
            }
            return null;
        }

        private void setNumericAssumption(State state, Primitive assumption) {
            final List<Symbolic> symbols = symbolsInNumericAssumption(assumption);
            this.s.append(INDENT_2);
            this.s.append("valueCalculator = new ValueCalculator() {\n");
            this.s.append(INDENT_3);
            this.s.append("@Override public Iterable<String> getVariableOrigins() {\n");
            this.s.append(INDENT_4);
            this.s.append("ArrayList<String> retVal = new ArrayList<>();\n");       
            for (Symbolic symbol: symbols) {
                this.s.append(INDENT_4);
                this.s.append("retVal.add(\"");
                this.s.append(getPossiblyAdaptedOriginString(symbol));
                this.s.append("\");\n");
            }
            this.s.append(INDENT_4);
            this.s.append("return retVal;\n");       
            this.s.append(INDENT_3);
            this.s.append("}\n");       
            this.s.append(INDENT_3);
            this.s.append("@Override public double calculate(List<Object> variables) {\n");
            for (int i = 0; i < symbols.size(); ++i) {
                final Symbolic symbol = symbols.get(i);
                makeVariableFor(symbol);
                this.s.append(INDENT_4);
                this.s.append("final ");
                this.s.append(javaType(symbol));
                this.s.append(" ");
                this.s.append(getVariableFor(symbol));
                this.s.append(" = (");
                this.s.append(javaType(symbol));
                this.s.append(") variables.get(");
                this.s.append(i);
                this.s.append(");\n");
            }
            this.s.append(INDENT_4);
            this.s.append("return ");
            this.s.append(javaAssumptionCheck(state, assumption));
            this.s.append(";\n");
            this.s.append(INDENT_3);
            this.s.append("}\n");
            this.s.append(INDENT_2);
            this.s.append("};\n");
            this.s.append(INDENT_2);
            this.s.append("pathConditionHandler.add(new SimilarityWithNumericExpression(valueCalculator));\n");
        }

        private String getPossiblyAdaptedOriginString(Symbolic symbol) {
            final String retVal = possiblyAdaptMapModelSymbols(symbol.asOriginString());
            return retVal;
        }

        private List<Symbolic> symbolsInNumericAssumption(Primitive e) {
            final ArrayList<Symbolic> symbols = new ArrayList<>();
            final PrimitiveVisitor v = new PrimitiveVisitor() {

                @Override
                public void visitWideningConversion(WideningConversion x) throws Exception {
                    x.getArg().accept(this);
                }

                @Override
                public void visitTerm(Term x) throws Exception { }

                @Override
                public void visitSimplex(Simplex x) throws Exception { }

                @Override
                public void visitPrimitiveSymbolicAtomic(PrimitiveSymbolicAtomic s) {
                    if (symbols.contains(s)) {
                        return;
                    }
                    symbols.add(s);
                }

                @Override
                public void visitNarrowingConversion(NarrowingConversion x) throws Exception {
                    x.getArg().accept(this);
                }

                @Override
                public void visitPrimitiveSymbolicApply(PrimitiveSymbolicApply x) throws Exception {
                    if (symbols.contains(x)) {
                        return; //surely its args have been processed
                    }
                    symbols.add(x);
                }

                @Override
                public void visitExpression(Expression e) throws Exception {
                    if (e.isUnary()) {
                        e.getOperand().accept(this);
                    } else {
                        e.getFirstOperand().accept(this);
                        e.getSecondOperand().accept(this);
                    }
                }

                @Override
                public void visitAny(Any x) { }
            };

            try {
                e.accept(v);
            } catch (Exception exc) {
                //this should never happen
                throw new AssertionError(exc);
            }
            return symbols;
        }

        private Operator dual(Operator op) {
            switch (op) {
            case AND:
                return Operator.OR;
            case OR:
                return Operator.AND;
            case GT:
                return Operator.LE;
            case GE:
                return Operator.LT;
            case LT:
                return Operator.GE;
            case LE:
                return Operator.GT;
            case EQ:
                return Operator.NE;
            case NE:
                return Operator.EQ;
            default:
                return null;
            }
        }

        private String javaAssumptionCheck(State state, Primitive assumption) {
            //first pass: Eliminate negation
            final ArrayList<Primitive> assumptionWithNoNegation = new ArrayList<>(); //we use only one element as it were a reference to a String variable            
            final PrimitiveVisitor negationEliminator = new PrimitiveVisitor() {
                @Override
                public void visitAny(Any x) throws Exception {
                    assumptionWithNoNegation.add(x);
                }

                @Override
                public void visitExpression(Expression e) throws Exception {
                    if (e.getOperator().equals(Operator.NOT)) {
                        final Primitive operand = e.getOperand();
                        if (operand instanceof Simplex) {
                            //true or false
                            assumptionWithNoNegation.add(MethodUnderTest.this.calc.push(operand).not().pop());
                        } else if (operand instanceof Expression) {
                            final Expression operandExp = (Expression) operand;
                            final Operator operator = operandExp.getOperator();
                            if (operator.equals(Operator.NOT)) {
                                //double negation
                                operandExp.getOperand().accept(this);
                            } else if (operator.equals(Operator.AND) || operator.equals(Operator.OR)) {
                                MethodUnderTest.this.calc.push(operandExp.getFirstOperand()).not().pop().accept(this);
                                final Primitive first = assumptionWithNoNegation.remove(0);
                                MethodUnderTest.this.calc.push(operandExp.getSecondOperand()).not().pop().accept(this);
                                final Primitive second = assumptionWithNoNegation.remove(0);
                                assumptionWithNoNegation.add(Expression.makeExpressionBinary(first, dual(operator), second));
                            } else if (operator.equals(Operator.GT) || operator.equals(Operator.GE) ||
                            operator.equals(Operator.LT) || operator.equals(Operator.LE) ||
                            operator.equals(Operator.EQ) || operator.equals(Operator.NE)) {
                                assumptionWithNoNegation.add(Expression.makeExpressionBinary(operandExp.getFirstOperand(), dual(operator), operandExp.getSecondOperand()));
                            } else {
                                //can't do anything for this expression
                                assumptionWithNoNegation.add(e);
                            }
                        } else {
                            //can't do anything for this expression
                            assumptionWithNoNegation.add(e);
                        }
                    } else if (e.isUnary()) {
                        //in this case the operator can only be NEG
                        assumptionWithNoNegation.add(e);
                    } else {
                        //binary operator
                        final Operator operator = e.getOperator();
                        e.getFirstOperand().accept(this);
                        final Primitive first = assumptionWithNoNegation.remove(0);
                        e.getSecondOperand().accept(this);
                        final Primitive second = assumptionWithNoNegation.remove(0);
                        assumptionWithNoNegation.add(Expression.makeExpressionBinary(first, operator, second));
                    }
                }

                @Override
                public void visitPrimitiveSymbolicApply(PrimitiveSymbolicApply x) throws Exception {
                    final ArrayList<Value> newArgs = new ArrayList<>(); 
                    for (Value arg : x.getArgs()) {
                        if (arg instanceof Primitive) {
                            ((Primitive) arg).accept(this);
                            newArgs.add(assumptionWithNoNegation.remove(0));
                        } else {
                            newArgs.add(arg);
                        }
                    }
                    assumptionWithNoNegation.add(new PrimitiveSymbolicApply(x.getType(), x.historyPoint(), x.getOperator(), newArgs.toArray(new Value[0])));
                }

                @Override
                public void visitPrimitiveSymbolicAtomic(PrimitiveSymbolicAtomic s) throws Exception {
                    assumptionWithNoNegation.add(s);
                }

                @Override
                public void visitSimplex(Simplex x) throws Exception {
                    assumptionWithNoNegation.add(x);
                }

                @Override
                public void visitTerm(Term x) throws Exception {
                    assumptionWithNoNegation.add(x);
                }

                @Override
                public void visitNarrowingConversion(NarrowingConversion x) throws Exception {
                    assumptionWithNoNegation.add(x);
                }

                @Override
                public void visitWideningConversion(WideningConversion x) throws Exception {
                    assumptionWithNoNegation.add(x);
                }

            };
            try {
                assumption.accept(negationEliminator);
            } catch (Exception exc) {
                //this may happen if Any appears in assumption
                throw new RuntimeException(exc);
            }

            //second pass: translate
            final ArrayList<String> translation = new ArrayList<String>(); //we use only one element as it were a reference to a String variable            
            final PrimitiveVisitor translator = new PrimitiveVisitor() {
                @Override
                public void visitWideningConversion(WideningConversion x) throws Exception {
                    x.getArg().accept(this);
                    final char argType = x.getArg().getType();
                    final char type = x.getType();
                    if (argType == Type.BOOLEAN && type == Type.INT) {
                        //operand stack widening of booleans
                        final String arg = translation.remove(0);
                        translation.add("((" + arg + ") == false ? 0 : 1)");
                    }
                }

                @Override
                public void visitTerm(Term x) {
                    translation.add(x.toString());
                }

                @Override
                public void visitSimplex(Simplex x) {
                    translation.add(x.getActualValue().toString());
                }

                @Override
                public void visitPrimitiveSymbolicAtomic(PrimitiveSymbolicAtomic s) {
                    makeVariableFor(s);
                    translation.add(getVariableFor(s));
                }

                @Override
                public void visitNarrowingConversion(NarrowingConversion x)
                throws Exception {
                    x.getArg().accept(this);
                    final String arg = translation.remove(0);
                    final StringBuilder b = new StringBuilder();
                    b.append("(");
                    b.append(javaPrimitiveType(x.getType()));
                    b.append(") (");
                    b.append(arg);
                    b.append(")");
                    translation.add(b.toString());
                }

                @Override
                public void visitPrimitiveSymbolicApply(PrimitiveSymbolicApply x)
                throws Exception {
                    makeVariableFor(x);
                    translation.add(getVariableFor(x));
                }

                @Override
                public void visitExpression(Expression e) throws Exception {
                    final StringBuilder b = new StringBuilder();
                    final Operator op = e.getOperator();
                    if (e.isUnary()) {
                        e.getOperand().accept(this);
                        final String arg = translation.remove(0);
                        b.append(op == Operator.NEG ? "-" : op.toString());
                        b.append("(");
                        b.append(arg);
                        b.append(")");
                    } else { 
                        e.getFirstOperand().accept(this);
                        final String firstArg = translation.remove(0);
                        e.getSecondOperand().accept(this);
                        final String secondArg = translation.remove(0);
                        if (op.equals(Operator.EQ) ||
                        op.equals(Operator.GT) ||
                        op.equals(Operator.LT) ||
                        op.equals(Operator.GE) ||
                        op.equals(Operator.LE)) {
                            b.append("(");
                            b.append(firstArg);
                            b.append(") ");
                            b.append(op.toString());
                            b.append(" (");
                            b.append(secondArg);
                            b.append(") ? 0 : isNaN((");
                            b.append(firstArg);
                            b.append(") - (");
                            b.append(secondArg);
                            b.append(")) ? BIG_DISTANCE : SMALL_DISTANCE + abs((");
                            b.append(firstArg);
                            b.append(") - (");
                            b.append(secondArg);
                            b.append("))");
                        } else if (op.equals(Operator.NE)) {
                            b.append("(");
                            b.append(firstArg);
                            b.append(") ");
                            b.append(op.toString());
                            b.append(" (");
                            b.append(secondArg);
                            b.append(") ? 0 : isNaN((");
                            b.append(firstArg);
                            b.append(") - (");
                            b.append(secondArg);
                            b.append(")) ? BIG_DISTANCE : SMALL_DISTANCE");
                        } else {
                            b.append("(");
                            b.append(firstArg);
                            b.append(") ");
                            if (op.equals(Operator.AND)) {
                                b.append("+");
                            } else if (op.equals(Operator.OR)) {
                                b.append("*");
                            } else {
                                b.append(op.toString());
                            }
                            b.append(" (");
                            b.append(secondArg);
                            b.append(")");
                        }
                    }
                    translation.add(b.toString());
                }

                @Override
                public void visitAny(Any x) throws Exception {
                    throw new Exception();
                }
            };
            try {
                assumptionWithNoNegation.get(0).accept(translator);
            } catch (Exception exc) {
                //this may happen if Any appears in assumption
                throw new RuntimeException(exc);
            }

            return translation.get(0);
        }

    }
}
