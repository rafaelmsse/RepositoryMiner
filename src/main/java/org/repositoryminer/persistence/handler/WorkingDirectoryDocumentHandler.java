package org.repositoryminer.persistence.handler;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.repositoryminer.persistence.Connection;

import com.mongodb.BasicDBObject;

public class WorkingDirectoryDocumentHandler extends DocumentHandler{

	private static final String COLLECTION_NAME = "rm_working_directories";

	public WorkingDirectoryDocumentHandler() {
		super.collection = Connection.getInstance().getCollection(COLLECTION_NAME);
	}
	
	@Override
	public Document findById(String id, Bson projection){
		BasicDBObject whereClause = new BasicDBObject();
		whereClause.put("_id", id);
		
		return findOne(whereClause, projection);
	}
	
}