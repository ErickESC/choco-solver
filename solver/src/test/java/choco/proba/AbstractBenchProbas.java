package choco.proba;

import solver.Solver;
import solver.constraints.Constraint;
import solver.constraints.IntConstraint;
import solver.constraints.nary.alldifferent.AllDifferent;
import solver.constraints.propagators.nary.alldifferent.proba.CondAllDiffBCProba;
import solver.propagation.generator.PArc;
import solver.propagation.generator.PCoarse;
import solver.propagation.generator.Queue;
import solver.recorders.conditions.ICondition;
import solver.search.loop.monitors.ISearchMonitor;
import solver.search.loop.monitors.VoidSearchMonitor;
import solver.search.measure.IMeasures;
import solver.search.strategy.StrategyFactory;
import solver.variables.IntVar;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: xavier lorca
 */
public abstract class AbstractBenchProbas {
    public static final String sep = ";";
    public static int TIMELIMIT = 60000;
    int seed;
    int size;
    Solver solver;
    IntVar[] allVars; // all the vairables of the problem
    IntVar[] vars; // decision variables for the problem
    Constraint[] cstrs; // all the cstrs involved in the problem (including alldiff)
    AllDifferent.Type type;
    boolean isProba;

    // Data
    public Data data;
    int nbTests;

    AbstractBenchProbas(Solver solver, int size, AllDifferent.Type type, int nbTests, int seed, boolean isProba) {
        this.solver = solver;
        this.type = type;
        this.seed = seed;
        this.size = size;
        this.isProba = isProba;
        this.nbTests = nbTests;
        this.data = new Data(solver, this, nbTests);
    }

    abstract void buildProblem(int size, boolean proba);

    public String executionLoop() {
        for (int i = 0; i < nbTests; i++) {
            if (i > 0) {
                restartProblem(seed + i);
                data.solver = solver;
            }
            execute();
            //System.out.println("custom: "+PropAllDiffAC_new.nbCustom);
            //System.out.println("full: "+PropAllDiffAC_new.nbFull);
            data.recordResults(i);
        }
        return data.getResults();
    }

    public String[] getDetails() {
        return data.details;
    }

    private void restartProblem(int seed) {
        this.solver = new Solver();
        this.seed = seed;
        data.hasEncounteredLimit = false;
    }

    private void execute() {
        //SearchMonitorFactory.log(solver, false, false);
        this.buildProblem(size, false);
        this.solver.post(this.cstrs);
        this.solver.getSearchLoop().getLimitsBox().setTimeLimit(TIMELIMIT);
        this.configSearchStrategy();
        this.configPropStrategy();
        this.solveProcess();
    }

    void solveProcess() {
        //SearchMonitorFactory.prop_count(solver);
        //SearchMonitorFactory.log(solver, false, false);
        this.solver.findSolution();
    }

    void configSearchStrategy() {
        //this.solver.set(StrategyFactory.random(this.vars, this.solver.getEnvironment(), this.seed));
        //this.solver.set(StrategyFactory.domwdegMindom(this.vars, this.solver));
        this.solver.set(StrategyFactory.minDomMinVal(this.vars, this.solver.getEnvironment()));
    }

    private void configPropStrategy() {
        Constraint[] cstrs = solver.getCstrs();
        Queue arcs = new Queue(new PArc(cstrs));
        Queue coarses;
//        if (!isProba) {
//            coarses = Queue.build(Primitive.coarses(cstrs));
//        } else {
        ActivateCond sm = new ActivateCond();
        solver.getSearchLoop().plugSearchMonitor(sm);
        List<PCoarse> coarses_ = new ArrayList<PCoarse>();
        for (int i = 0; i < cstrs.length; i++) {
            if (isProba && cstrs[i] instanceof AllDifferent) {
                IntConstraint icstr = (AllDifferent) cstrs[i];
                IntVar[] myvars = icstr.getVariables();
                ICondition condition = new CondAllDiffBCProba(solver.getEnvironment(), myvars, seed);
                sm.add(condition);
            }
            coarses_.add(new PCoarse(cstrs[i]));
        }
        coarses = new Queue(coarses_.toArray(new PCoarse[coarses_.size()]));
//        }
        solver.set(new Queue(arcs.clearOut(), coarses.pickOne()).clearOut());
    }//*/

    public String toString() {
        if (!isProba) {
            return "" + type;
        } else {
            return "" + type + "-prob";
        }
    }

    private static class ActivateCond extends VoidSearchMonitor implements ISearchMonitor {

        List<ICondition> conds = new ArrayList<ICondition>();

        public void add(ICondition cond) {
            conds.add(cond);
        }

        @Override
        public void afterInitialize() {
            for (ICondition cond : conds) {
                cond.activate();
            }
        }
    }

    private class Data {

        Solver solver;
        AbstractBenchProbas pb;

        // output data
        private int nbTests; // number of tests executed
        private long[] nbSolutions;
        private long[] nbNodes;
        private long[] nbFails;
        private long[] nbFinePropag;
        private long[] nbPropag; // number of propagation for the meta-propagator of alldiff
        private double[] ratioPropagByNodes;
        private double[] initTime;
        private double[] firstPropag;
        private double[] time;

        // output averages
        private double avgSolutions;
        private double avgNodes;
        private double avgFails;
        private double avgFinePropag;
        private double avgPropag;
        private double avgRatio;
        private double avgInitTime;
        private double avgFirstPropag;
        private double avgTime;

        private boolean hasEncounteredLimit;

        private String[] details;

        private Data(Solver solver, AbstractBenchProbas pb, int nbTests) {
            this.solver = solver;
            this.pb = pb;
            this.nbTests = nbTests;
            this.nbSolutions = new long[nbTests];
            this.nbNodes = new long[nbTests];
            this.nbFails = new long[nbTests];
            this.nbFinePropag = new long[nbTests];
            this.nbPropag = new long[nbTests];
            this.ratioPropagByNodes = new double[nbTests];
            this.initTime = new double[nbTests];
            this.time = new double[nbTests];
            this.firstPropag = new double[nbTests];
            this.hasEncounteredLimit = false;
        }

        public void recordResults(int it) {
            IMeasures mes = this.solver.getMeasures();
            //this.nbSolutions = mes.getObjectiveValue();//mes.getSolutionCount();
            this.nbSolutions[it] = mes.getSolutionCount();
            this.nbNodes[it] = mes.getNodeCount();
            this.nbFails[it] = mes.getFailCount();
            this.nbFinePropag[it] = mes.getEventsCount();
            this.nbPropag[it] = mes.getPropagationsCount(); //+ mes.getEventsCount();  => on compte juste les propag lourdes
            if (this.nbNodes[it] > 0) {
                this.ratioPropagByNodes[it] = ((double) this.nbPropag[it]) / this.nbNodes[it];
            } else {
                this.ratioPropagByNodes[it] = this.nbPropag[it]; // on a que le noeud root
            }

            if (this.solver.getMeasures().getTimeCount() < TIMELIMIT) {
                this.time[it] = mes.getTimeCount();//-mes.getInitialisationTimeCount();
            } else {
                this.hasEncounteredLimit = true;
                this.time[it] = 0;
            }
            this.initTime[it] = mes.getInitialisationTimeCount();
            this.firstPropag[it] = mes.getInitialPropagationTimeCount();
        }

        private void recordAverage() {
            long sumSolutions = 0;
            long sumNodes = 0;
            long sumFails = 0;
            long sumFinePropag = 0;
            long sumPropag = 0;
            double sumRatio = 0;
            double sumTime = 0;
            double sumInitTime = 0;
            double sumInitPropag = 0;
            for (int i = 0; i < nbTests; i++) {
                sumSolutions += nbSolutions[i];
                sumNodes += nbNodes[i];
                sumFails += nbFails[i];
                sumFinePropag += nbFinePropag[i];
                sumPropag += nbPropag[i];
                sumRatio += ratioPropagByNodes[i];
                sumTime += time[i];
                sumInitTime += initTime[i];
                sumInitPropag += firstPropag[i];
            }
            this.avgSolutions = (double) sumSolutions / nbTests;
            this.avgNodes = (double) sumNodes / nbTests;
            this.avgFails = (double) sumFails / nbTests;
            this.avgFinePropag = (double) sumFinePropag / nbTests;
            this.avgPropag = (double) sumPropag / nbTests;
            this.avgRatio = sumRatio / nbTests;
            if (hasEncounteredLimit) {
                this.avgTime = -1;
            } else {
                this.avgTime = sumTime / nbTests;
            }
            this.avgInitTime = sumInitTime / nbTests;
            this.avgFirstPropag = sumInitPropag / nbTests;
        }

        public String getResults() {
            this.recordAverage();
            details = new String[nbTests];
            for (int i = 0; i < nbTests; i++) {
                details[i] = nbSolutions[i] + sep + nbNodes[i] + sep + nbFails[i] + sep + nbFinePropag[i] + sep + nbPropag[i] + sep + ratioPropagByNodes[i] + sep + initTime[i] + sep + firstPropag[i] + sep + time[i] + sep;
            }
            return avgSolutions + sep + avgNodes + sep + avgFails + sep + avgFinePropag + sep + avgPropag + sep + avgRatio + sep + avgInitTime + sep + avgFirstPropag + sep + avgTime + sep;
        }

    }


}
