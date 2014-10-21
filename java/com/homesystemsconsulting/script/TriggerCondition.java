package com.homesystemsconsulting.script;

import java.util.List;

import com.homesystemsconsulting.events.Bus;
import com.homesystemsconsulting.events.Event;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.AndExpressionContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.AtomExpressionContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.BooleanComparisonContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.EventContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.NotExpressionContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.NumberComparisonContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.OrExpressionContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.StableEventContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.StringComparisonContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.TransientEventContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.TriggerContext;
import com.homesystemsconsulting.script.parser.SferaScriptGrammarParser.UnknownComparisonContext;

public class TriggerCondition {

	private final TriggerContext condition;
	
	/**
	 * 
	 * @param condition
	 */
	public TriggerCondition(TriggerContext condition) {
		this.condition = condition;
	}

	/**
	 * 
	 * @param event
	 * @return
	 * @throws Exception 
	 */
	public boolean eval(Event event) throws Exception {
		return eval(condition.orExpression(), event);
	}

	/**
	 * 
	 * @param ctx
	 * @param event
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(OrExpressionContext ctx, Event event) throws Exception {
		List<AndExpressionContext> ands = ctx.andExpression();
		
		boolean res = eval(ands.get(0), event);
		for (int i = 1; i < ands.size(); i++) {
			res = res || eval(ands.get(i), event);
		}
		
		return res;
	}

	/**
	 * 
	 * @param ctx
	 * @param event
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(AndExpressionContext ctx, Event event) throws Exception {
		List<NotExpressionContext> nots = ctx.notExpression();
		
		boolean res = eval(nots.get(0), event);
		for (int i = 1; i < nots.size(); i++) {
			res = res && eval(nots.get(i), event);
		}
		
		return res;
	}

	/**
	 * 
	 * @param ctx
	 * @param event
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(NotExpressionContext ctx, Event event) throws Exception {
		if (ctx.NOT() != null) {
			return !eval(ctx.atomExpression(), event);
		} else {
			return eval(ctx.atomExpression(), event);
		}
	}

	/**
	 * 
	 * @param ctx
	 * @param event
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(AtomExpressionContext ctx, Event event) throws Exception {
		if (ctx.event() != null) {
			return eval(ctx.event(), event);
		} else {
			return eval(ctx.orExpression(), event);
		}
	}

	/**
	 * 
	 * @param ctx
	 * @param event
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(EventContext ctx, Event event) throws Exception {
		if (ctx.stableEvent() != null) {
			return eval(ctx.stableEvent());
		} else {
			return eval(ctx.transientEvent(), event);
		}
	}

	/**
	 * 
	 * @param ctx
	 * @param event
	 * @return
	 */
	private boolean eval(TransientEventContext ctx, Event event) {
		String eventId = event.getId();
		String condition = ctx.getText();
		if (event.getId().startsWith(ctx.getText())) {
			if (eventId.length() == condition.length()) { // equal
				return true;
			}
			
			if (eventId.charAt(condition.length()) == '.' ||
					eventId.charAt(condition.length()) == '(') {
				/*
				 *  meaning: 
				 *  eventId.startsWith(condition + ".") || 
				 *  	eventId.startsWith(condition + "(")
				 */
				return true;
			}
		}
			
		return false;
	}
	
	/**
	 * 
	 * @param ctx
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(StableEventContext ctx) throws Exception {
		if (ctx.stringComparison() != null) {
			return eval(ctx.stringComparison());
		} else if (ctx.numberComparison() != null) {
			return eval(ctx.numberComparison());
		} else if (ctx.booleanComparison() != null) {
			return eval(ctx.booleanComparison());
		} else { // 'unknown' comparison
			return eval(ctx.unknownComparison());
		}
	}

	/**
	 * 
	 * @param ctx
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(StringComparisonContext ctx) throws Exception {
		Object value = Bus.getEventValue(ctx.FinalNodeId().getText());
		
		if (value == null) {
			return false;
		}
		
		if (!(value instanceof String)) {
			int line = ctx.getStart().getLine();
			throw new Exception("line " + line + ": Type error: " + ctx.FinalNodeId().getText() + " not a string");
		}
		
		String literal = ctx.StringLiteral().getText();
		literal = literal.substring(1, literal.length() - 1);
		
		if (ctx.ET() != null) {
			return literal.equals(value);
		} else if (ctx.NE() != null) {
			return !literal.equals(value);
		} else if (ctx.GT() != null) {
			return ((String) value).compareTo(literal) > 0;
		} else if (ctx.LT() != null) {
			return ((String) value).compareTo(literal) < 0;
		} else if (ctx.GE() != null) {
			return ((String) value).compareTo(literal) >= 0;
		} else { // LE
			return ((String) value).compareTo(literal) <= 0;
		}
	}
	
	/**
	 * 
	 * @param ctx
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(NumberComparisonContext ctx) throws Exception {
		Object value = Bus.getEventValue(ctx.FinalNodeId().getText());
		
		if (value == null) {
			return false;
		}
		
		if (!(value instanceof Double)) {
			int line = ctx.getStart().getLine();
			throw new Exception("line " + line + ": Type error: " + ctx.FinalNodeId().getText() + " not a number");
		}
		
		if (ctx.ET() != null) {
			return (Double) value == Double.parseDouble(ctx.NumberLiteral().getText());
		} else if (ctx.NE() != null) {
			return (Double) value != Double.parseDouble(ctx.NumberLiteral().getText());
		} else if (ctx.GT() != null) {
			return (Double) value > Double.parseDouble(ctx.NumberLiteral().getText());
		} else if (ctx.LT() != null) {
			return (Double) value < Double.parseDouble(ctx.NumberLiteral().getText());
		} else if (ctx.GE() != null) {
			return (Double) value >= Double.parseDouble(ctx.NumberLiteral().getText());
		} else { // LE
			return (Double) value <= Double.parseDouble(ctx.NumberLiteral().getText());
		}
	}
	
	/**
	 * 
	 * @param ctx
	 * @return
	 * @throws Exception 
	 */
	private boolean eval(BooleanComparisonContext ctx) throws Exception {
		Object value = Bus.getEventValue(ctx.FinalNodeId().getText());
		
		if (value == null) {
			return false;
		}
		
		if (!(value instanceof Boolean)) {
			int line = ctx.getStart().getLine();
			throw new Exception("line " + line + ": Type error: " + ctx.FinalNodeId().getText() + " not a boolean");
		}
		
		if (ctx.ET() != null) {
			return Boolean.parseBoolean(ctx.BooleanLiteral().getText()) == (Boolean) value;
		} else { // NE
			return Boolean.parseBoolean(ctx.BooleanLiteral().getText()) != (Boolean) value;
		}
	}
	
	/**
	 * 
	 * @param ctx
	 * @return
	 */
	private boolean eval(UnknownComparisonContext ctx) {
		Object value = Bus.getEventValue(ctx.FinalNodeId().getText());
		
		if (ctx.ET() != null) {
			return value == null;
		} else { // NE
			return value != null;
		}
	}

}
