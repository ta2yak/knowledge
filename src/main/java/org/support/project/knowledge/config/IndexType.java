package org.support.project.knowledge.config;

public enum IndexType {
	knowledge,
	KnowledgeFile,
	KnowledgeComment;
	
	
	public int getValue() {
		return ordinal();
	}
	
	public static IndexType getType(int type) {
		IndexType[] values = values();
		return values[type];
	}

}
