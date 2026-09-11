package com.imo.operator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/** Stateful session for LUNA's operator loop. It keeps the user's goal, last actions and failures. */
public final class OperatorState {
    private final String goal;
    private final long startedAt=System.currentTimeMillis();
    private final Deque<String> events=new ArrayDeque<>();
    private String lastAction="";
    private String lastPackage="";
    private String lastSnapshot="";
    private int actionCount=0;
    private int recoveryCount=0;

    public OperatorState(String goal){this.goal=goal==null?"":goal.trim();}
    public String goal(){return goal;}
    public String lastAction(){return lastAction;}
    public String lastPackage(){return lastPackage;}
    public String lastSnapshot(){return lastSnapshot;}
    public int actionCount(){return actionCount;}
    public int recoveryCount(){return recoveryCount;}
    public void observed(String pkg,String snapshot){lastPackage=pkg==null?"":pkg;lastSnapshot=snapshot==null?"":snapshot;}
    public void acted(String action){lastAction=action==null?"":action;actionCount++;add("ACTION "+lastAction);}
    public void recovered(){recoveryCount++;add("RECOVERY #"+recoveryCount);}
    public void add(String event){if(event==null||event.trim().isEmpty())return;events.addLast(event);while(events.size()>12)events.removeFirst();}
    public String recent(){StringBuilder s=new StringBuilder();for(String e:events){if(s.length()>0)s.append("\n");s.append(e);}return s.toString();}
    public boolean timedOut(long ms){return System.currentTimeMillis()-startedAt>ms;}
    public String summary(){return String.format(Locale.ROOT,"goal=%s actions=%d recoveries=%d",goal,actionCount,recoveryCount);}
}
