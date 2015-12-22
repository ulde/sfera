/**
 * 
 */
package cc.sferalabs.sfera.script;

import cc.sferalabs.sfera.events.Event;

/**
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class ScriptEvent implements Event {

	private final Object source;
	private final String id;
	private final String subId;
	private final long timestamp;
	private final Object value;

	/**
	 * @param source
	 * @param sourceId
	 * @param id
	 * @param value
	 */
	public ScriptEvent(Object source, String sourceId, String id, Object value) {
		this.timestamp = System.currentTimeMillis();
		this.source = source;
		this.id = sourceId + "." + id;
		this.subId = id;
		this.value = value;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getSubId() {
		return subId;
	}

	@Override
	public Object getSource() {
		return source;
	}

	@Override
	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	public Object getScriptConditionValue() {
		return getValue();
	}

}
