package edu.rpi.aris.gui;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Premise;
import edu.rpi.aris.proof.SentenceUtil;
import edu.rpi.aris.rules.RuleList;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.*;
import javafx.scene.image.Image;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

public class Proof {

    private ObservableMap<Line, Integer> lineLookup = FXCollections.observableHashMap();
    private ObservableList<Line> lines = FXCollections.observableArrayList();
    private ObservableList<Goal> goals = FXCollections.observableArrayList();
    private SimpleIntegerProperty numLines = new SimpleIntegerProperty();
    private SimpleIntegerProperty numPremises = new SimpleIntegerProperty();

    public Proof() {
        numLines.bind(Bindings.size(lines));
    }

    public IntegerProperty numLinesProperty() {
        return numLines;
    }

    public IntegerProperty numPremises() {
        return numPremises;
    }

    public ObservableList<Line> getLines() {
        return lines;
    }

    public Proof.Line addLine(int index, boolean isAssumption, int subProofLevel) {
        if (index <= lines.size()) {
            Line l = new Line(subProofLevel, isAssumption, this);
            lines.add(index, l);
            lineLookup.put(l, index);
            for (int i = index + 1; i < lines.size(); ++i)
                lineLookup.put(lines.get(i), i);
            l.lineNumberProperty().bind(Bindings.createIntegerBinding(() -> lineLookup.get(l), lineLookup));
            return l;
        } else
            return null;
    }

    public Proof.Line addPremise() {
        Line line = addLine(numPremises.get(), true, 0);
        line.isUnderlined().bind(Bindings.createBooleanBinding(() -> line.lineNumber.get() == numPremises.get() - 1, numPremises));
        numPremises.set(numPremises.get() + 1);
        return line;
    }

    public Goal addGoal() {
        Goal goal = new Goal();
        goal.goalNum.bind(Bindings.createIntegerBinding(() -> goals.indexOf(goal), goals));
        goals.add(goal);
        return goal;
    }

    public void removeGoal(int goalNum) {
        goals.remove(goalNum);
    }

    public ObservableList<Goal> getGoals() {
        return goals;
    }

    public HashSet<Integer> getPossiblePremiseLines(Line line) {
        HashSet<Integer> possiblePremise = new HashSet<>();
        int maxLvl = line.subProofLevel.get() + 1;
        for (int i = line.lineNumber.get() - 1; i >= 0; i--) {
            Line p = lines.get(i);
            if (p.subProofLevel.get() == maxLvl && p.isAssumption) {
                possiblePremise.add(i);
            } else if (p.subProofLevel.get() < maxLvl) {
                possiblePremise.add(i);
                if (p.subProofLevel.get() < maxLvl && p.isAssumption)
                    maxLvl = p.subProofLevel.get();
            }
        }
        return possiblePremise;
    }

    public void togglePremise(int selected, Line premise) {
        if (selected < 0)
            return;
        Line line = lines.get(selected);
        if (line.isAssumption || premise.lineNumber.get() >= selected)
            return;
        HashSet<Integer> canSelect = getPossiblePremiseLines(line);
        for (int i = premise.lineNumber.get(); i >= 0; i--) {
            if (canSelect.contains(i)) {
                premise = lines.get(i);
                break;
            }
        }
        if (!line.removePremise(premise))
            line.addPremise(premise);
    }

    public HashSet<Line> getHighlighted(Line line) {
        HashSet<Line> highlight = new HashSet<>(line.premises);
        for (Line p : line.premises) {
            HashSet<Line> highlighted = new HashSet<>();
            int lineNum = p.lineNumber.get();
            if (p.isAssumption() && lineNum + 1 < lines.size()) {
                int indent = p.subProofLevelProperty().get();
                Proof.Line l = lines.get(lineNum + 1);
                while (l != null && (l.subProofLevelProperty().get() > indent || (l.subProofLevelProperty().get() == indent && !l.isAssumption()))) {
                    highlighted.add(l);
                    if (lineNum + 1 == lines.size())
                        l = null;
                    else
                        l = lines.get(++lineNum);
                }
            }
            if (!highlighted.contains(line))
                highlight.addAll(highlighted);
            highlight.add(p);
        }
        return highlight;
    }

    public void delete(int lineNum) {
        if (lineNum > 0 || (numPremises.get() > 1 && lineNum >= 0)) {
            for (Line l : lines)
                l.lineDeleted(lines.get(lineNum));
            lines.remove(lineNum);
            for (int i = lineNum; i < lines.size(); ++i)
                lineLookup.put(lines.get(i), i);
            if (numPremises.get() > 1 && lineNum < numPremises.get()) {
                numPremises.set(numPremises.get() - 1);
            }
        }
    }

    public ArrayList<Status> verifyProof() {
        ArrayList<Status> goalStatus = new ArrayList<>();
        for (Goal g : goals) {
            if (g.expression == null && !g.buildExpression())
                goalStatus.add(Status.INVALID_EXPRESSION);
            else {
                Line line = findGoal(g.expression);
                if (line == null) {
                    g.goalStatus.set(Status.INVALID_CLAIM);
                    g.setStatus("This goal is not a top level statement in the proof");
                    goalStatus.add(Status.INVALID_CLAIM);
                } else {
                    boolean valid = recursiveLineVerification(line);
                    g.goalStatus.set(valid ? Status.CORRECT : Status.INVALID_CLAIM);
                    g.setStatus(valid ? "Congratulations! You proved the goal!" : "The goal does not follow from the support steps");
                    goalStatus.add(g.goalStatus.get());
                }
            }
        }
        return goalStatus;
    }

    private boolean recursiveLineVerification(Line l) {
        if (l.isAssumption || l.status.get() == Status.CORRECT)
            return true;
        if (l.status.get() == Status.NONE) {
            l.buildClaim();
            if (l.claim == null || !l.verifyClaim())
                return false;
            for (Line p : l.premises)
                if (!recursiveLineVerification(p))
                    return false;
            return true;
        } else
            return false;
    }

    private Line findGoal(Expression e) {
        if (e == null)
            return null;
        for (int i = numLines.get() - 1; i >= 0; --i) {
            Line l = lines.get(i);
            if (l.expression == null)
                l.buildExpression();
            if (l.subProofLevel.get() == 0 && l.expression != null && l.expression.equals(e))
                return l;
        }
        return null;
    }

    private Line getSubProofConclusion(Line assumption, Line goal) {
        int lvl = assumption.subProofLevel.get();
        if (!assumption.isAssumption || lvl == 0)
            return null;
        for (int i = assumption.lineNumber.get() + 1; i < numLines.get(); ++i) {
            Line l = lines.get(i);
            if (l == goal)
                return null;
            if (l.subProofLevel.get() < lvl || (l.subProofLevel.get() == lvl && l.isAssumption))
                return lines.get(i - 1);
        }
        return null;
    }

    private void resetGoalStatus() {
        for (Goal g : goals) {
            g.goalStatus.set(Status.NONE);
            g.buildExpression();
        }
    }

    public enum Status {

        NONE("no_icon.png"),
        INVALID_EXPRESSION("warning.png"),
        NO_RULE("question_mark.png"),
        INVALID_CLAIM("red_x.png"),
        CORRECT("check_mark.png");

        public final Image img;

        Status(String imgLoc) {
            img = new Image(Status.class.getResourceAsStream(imgLoc));
        }

    }

    public static class Line {

        private final boolean isAssumption;
        private SimpleIntegerProperty lineNumber = new SimpleIntegerProperty();
        private SimpleStringProperty expressionString = new SimpleStringProperty();
        private ObservableSet<Line> premises = FXCollections.observableSet();
        private SimpleIntegerProperty subProofLevel = new SimpleIntegerProperty();
        private SimpleBooleanProperty underlined = new SimpleBooleanProperty();
        private SimpleObjectProperty<RuleList> selectedRule = new SimpleObjectProperty<>(null);
        private SimpleObjectProperty<Status> status = new SimpleObjectProperty<>(Status.NONE);
        private Expression expression = null;
        private Claim claim = null;
        private SimpleStringProperty statusMsg = new SimpleStringProperty();
        private Timer parseTimer = null;
        private Proof proof;
        private ChangeListener<String> expressionChangeListener = (observableValue, s, t1) -> status.set(Status.NONE);

        private Line(int subProofLevel, boolean assumption, Proof proof) {
            isAssumption = assumption;
            underlined.set(isAssumption);
            this.proof = proof;
            this.subProofLevel.set(subProofLevel);
            expressionString.addListener((observableValue, oldVal, newVal) -> {
                synchronized (Line.this) {
                    expression = null;
                    claim = null;
                    startTimer();
                    proof.resetGoalStatus();
                }
            });
            selectedRule.addListener((observableValue, oldVal, newVal) -> {
                verifyClaim();
                proof.resetGoalStatus();
            });
            premises.addListener((SetChangeListener<Line>) change -> {
                verifyClaim();
                proof.resetGoalStatus();
            });
        }

        public IntegerProperty lineNumberProperty() {
            return lineNumber;
        }

        public IntegerProperty subProofLevelProperty() {
            return subProofLevel;
        }

        public SimpleObjectProperty<Status> statusProperty() {
            return status;
        }

        public boolean isAssumption() {
            return isAssumption;
        }

        public synchronized ObservableSet<Line> getPremises() {
            return premises;
        }

        private synchronized void addPremise(Line premise) {
            premises.add(premise);
            premise.expressionString.addListener(expressionChangeListener);
        }

        private synchronized boolean removePremise(Line premise) {
            premise.expressionString.removeListener(expressionChangeListener);
            return premises.remove(premise);
        }

        private void lineDeleted(Line deletedLine) {
            removePremise(deletedLine);
        }

        private synchronized void buildExpression() {
            String str = expressionString.get();
            if (str.trim().length() > 0) {
                try {
                    claim = null;
                    expression = new Expression(SentenceUtil.toPolishNotation(str));
                    setStatus("");
                    status.set(Status.NONE);
                } catch (ParseException e) {
                    setStatus(e.getMessage());
                    status.set(Status.INVALID_EXPRESSION);
                    expression = null;
                }
            } else {
                expression = null;
                setStatus("");
                status.set(Status.NONE);
            }
        }

        private void setStatus(String status) {
            Platform.runLater(() -> statusMsg.set(status));
        }

        public Premise[] getClaimPremises() {
            Premise[] premises = new Premise[this.premises.size()];
            int i = 0;
            for (Line p : this.premises) {
                p.stopTimer();
                p.buildExpression();
                if (p.expression == null) {
                    setStatus("The expression at line " + (p.lineNumber.get() + 1) + " is invalid");
                    status.set(Status.INVALID_CLAIM);
                    return null;
                }
                Line conclusion;
                if (p.isAssumption && (conclusion = proof.getSubProofConclusion(p, this)) != null) {
                    conclusion.buildExpression();
                    if (conclusion.expression == null) {
                        setStatus("The expression at line " + (p.lineNumber.get() + 1) + " is invalid");
                        status.set(Status.INVALID_CLAIM);
                        return null;
                    }
                    premises[i] = new Premise(p.expression, conclusion.expression);
                } else
                    premises[i] = new Premise(p.expression);
                ++i;
            }
            return premises;
        }

        private synchronized void buildClaim() {
            buildExpression();
            if (expression == null || isAssumption)
                return;
            if (selectedRule.get() == null) {
                setStatus("Rule Not Specified");
                status.set(Status.NO_RULE);
                return;
            }
            Premise[] premises = getClaimPremises();
            claim = new Claim(expression, premises, selectedRule.get().rule);
        }

        public boolean verifyClaim() {
            return verifyClaim(true);
        }

        private synchronized boolean verifyClaim(boolean stopTimer) {
            if (stopTimer)
                stopTimer();
            buildClaim();
            if (claim != null) {
                String result = claim.isValidClaim();
                if (result == null) {
                    setStatus("Line is Correct!");
                    status.set(Status.CORRECT);
                } else {
                    setStatus(result);
                    status.set(Status.INVALID_CLAIM);
                }
                return result == null;
            }
            return false;
        }

        public SimpleBooleanProperty isUnderlined() {
            return underlined;
        }

        public SimpleStringProperty expressionStringProperty() {
            return expressionString;
        }

        public SimpleObjectProperty<RuleList> selectedRuleProperty() {
            return selectedRule;
        }

        public SimpleStringProperty statusMsgProperty() {
            return statusMsg;
        }

        private synchronized void startTimer() {
            if (!Aris.isGUI())
                return;
            stopTimer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    synchronized (Line.this) {
                        parseTimer = null;
                        verifyClaim(false);
                    }
                }
            };
            parseTimer = new Timer(true);
            parseTimer.schedule(task, 1000);
        }

        private synchronized void stopTimer() {
            if (parseTimer != null) {
                parseTimer.cancel();
                parseTimer = null;
            }
        }

    }

    public static class Goal {
        private SimpleIntegerProperty goalNum = new SimpleIntegerProperty();
        private SimpleStringProperty goalString = new SimpleStringProperty();
        private SimpleStringProperty statusString = new SimpleStringProperty();
        private SimpleObjectProperty<Status> goalStatus = new SimpleObjectProperty<>(Status.NONE);
        private Expression expression = null;
        private Timer parseTimer = null;

        public Goal() {
            goalString.addListener((observableValue, s, t1) -> {
                expression = null;
                startTimer();
            });
        }

        public SimpleIntegerProperty goalNumProperty() {
            return goalNum;
        }

        public SimpleStringProperty goalStringProperty() {
            return goalString;
        }

        public SimpleStringProperty statusStringProperty() {
            return statusString;
        }

        public SimpleObjectProperty<Status> goalStatusProperty() {
            return goalStatus;
        }

        private synchronized boolean buildExpression() {
            stopTimer();
            String str = goalString.get();
            if (str.trim().length() > 0) {
                try {
                    expression = new Expression(SentenceUtil.toPolishNotation(str));
                    setStatus("");
                    goalStatus.set(Status.NONE);
                    return true;
                } catch (ParseException e) {
                    setStatus(e.getMessage());
                    goalStatus.set(Status.INVALID_EXPRESSION);
                    expression = null;
                    return false;
                }
            } else {
                expression = null;
                setStatus("");
                goalStatus.set(Status.NONE);
                return false;
            }
        }

        private void setStatus(String status) {
            Platform.runLater(() -> statusString.set(status));
        }

        private synchronized void startTimer() {
            if (!Aris.isGUI())
                return;
            stopTimer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    synchronized (Goal.this) {
                        parseTimer = null;
                        buildExpression();
                    }
                }
            };
            parseTimer = new Timer(true);
            parseTimer.schedule(task, 1000);
        }

        private synchronized void stopTimer() {
            if (parseTimer != null) {
                parseTimer.cancel();
                parseTimer = null;
            }
        }

    }

}