package br.edu.ufba.softvis.visminer.persistence;

import javax.persistence.EntityManager;

import br.edu.ufba.softvis.visminer.constant.MetricUid;
import br.edu.ufba.softvis.visminer.constant.SoftwareUnitType;
import br.edu.ufba.softvis.visminer.model.bean.Commit;
import br.edu.ufba.softvis.visminer.model.bean.File;
import br.edu.ufba.softvis.visminer.model.bean.SoftwareUnit;
import br.edu.ufba.softvis.visminer.model.database.FileDB;
import br.edu.ufba.softvis.visminer.model.database.MetricValueDB;
import br.edu.ufba.softvis.visminer.model.database.MetricValuePK;
import br.edu.ufba.softvis.visminer.model.database.RepositoryDB;
import br.edu.ufba.softvis.visminer.model.database.SoftwareUnitDB;
import br.edu.ufba.softvis.visminer.model.database.SoftwareUnitXCommitDB;
import br.edu.ufba.softvis.visminer.model.database.SoftwareUnitXCommitPK;
import br.edu.ufba.softvis.visminer.persistence.dao.MetricValueDAO;
import br.edu.ufba.softvis.visminer.persistence.dao.SoftwareUnitDAO;
import br.edu.ufba.softvis.visminer.persistence.dao.SoftwareUnitXCommitDAO;
import br.edu.ufba.softvis.visminer.persistence.impl.MetricValueDAOImpl;
import br.edu.ufba.softvis.visminer.persistence.impl.SoftwareUnitDAOImpl;
import br.edu.ufba.softvis.visminer.persistence.impl.SoftwareUnitXCommitImpl;
import br.edu.ufba.softvis.visminer.utility.StringUtils;

/**
 * @author Felipe Gustavo de Souza Gomes (felipegustavo1000@gmail.com)
 * @version 0.9
 * 
 * Persistence interface for metrics calculation.
 */
public class MetricPersistance {

	private String repositoryPath;
	private int repositoryId;
	
	private EntityManager entityManager;

	private SoftwareUnitDAO softUnitDao;
	private SoftwareUnitXCommitDAO softUnitXCommitDao;
	private MetricValueDAO metricValueDao;

	private Commit commit;
	private MetricUid metric;
	
	public MetricPersistance(String repositoryPath, int repositoryId, EntityManager entityManager) {

		this.repositoryId = repositoryId;
		this.repositoryPath = repositoryPath;
		this.entityManager = entityManager;

		this.softUnitDao = new SoftwareUnitDAOImpl();
		this.softUnitDao.setEntityManager(entityManager);

		this.softUnitXCommitDao = new SoftwareUnitXCommitImpl();
		this.softUnitXCommitDao.setEntityManager(entityManager);

		this.metricValueDao = new MetricValueDAOImpl();
		this.metricValueDao.setEntityManager(entityManager);

	}

	/**
	 * @param metric
	 * Sets the metric that is being calculated at time.
	 */
	public void setMetric(MetricUid metric) {
		this.metric = metric;
	}

	/**
	 * @param commit
	 * Sets the current commit of metric calculation.
	 */
	public void setCommit(Commit commit){
		this.commit = commit;
	}
	
	/**
	 * 
	 * @param file
	 * @param softwareUnit
	 * @return Software unit saved.
	 * Saves software unit in database and returns the object saved.
	 * It's necessary to save each software entity, even if you do not want to calculate the metrics over it.
	 * Keep the software unit hierarchy is important to VisMiner to generate unique ids and maintain data integrity.
	 * Don't worry about the metrics saving the same software entity, VisMiner take care of it for you, ensuring that exists only one record in database.
	 * Avoid to send all the software units at the same time, as a tree, for example, save one by one, saving first the parents and after the children.
	 * This method is responsible for returns the software unit with some useful data like database id and its unique char sequence.
	 * For example, if you want save the software unit "class A", you have to save first the software unit "package A", that contains class A and so on.
	 * If the software unit is not trailer with a file, as package, for example, pass file as null.
	 * Use this method if you only want to save the software unit and not save its metric value.
	 * 
	 */
	public SoftwareUnit saveSoftwareUnit(File file, SoftwareUnit softwareUnit){

		String uid = generateUid(file, softwareUnit.getParentUnit(), softwareUnit.getFullName(), softwareUnit.getType());
		SoftwareUnitDB softUnitDb = softUnitDao.findByUid(uid);

		// insert in database
		if(softUnitDb == null){

			softwareUnit.setUid(uid);;
			softUnitDb = new SoftwareUnitDB(0, softwareUnit.getFullName(), softwareUnit.getName(), 
					softwareUnit.getType(), softwareUnit.getUid());
			
			RepositoryDB repoDb = new RepositoryDB();
			repoDb.setId(repositoryId);
			softUnitDb.setRepository(repoDb);

			if(file != null){
				FileDB fileDb = new FileDB();
				fileDb.setId(file.getId());
				softUnitDb.setFile(fileDb);
			}

			if(softwareUnit.getParentUnit() != null){
				SoftwareUnitDB softUnitParent = new SoftwareUnitDB();
				softUnitParent.setId(softwareUnit.getParentUnit().getId());
				softUnitDb.setSoftwareUnit(softUnitParent);
			}

			softUnitDao.save(softUnitDb);
			SoftwareUnitXCommitPK softXCommitPk = new SoftwareUnitXCommitPK(softUnitDb.getId(), commit.getId());
			softUnitXCommitDao.save(new SoftwareUnitXCommitDB(softXCommitPk));

		}else{

			SoftwareUnitXCommitPK softXCommitPk = new SoftwareUnitXCommitPK(softUnitDb.getId(), commit.getId());
			SoftwareUnitXCommitDB softXCommitDb = softUnitXCommitDao.find(softXCommitPk);

			if(softXCommitDb == null){
				softXCommitDb = new SoftwareUnitXCommitDB(softXCommitPk);
				softUnitXCommitDao.save(softXCommitDb);
			}

		}

		return new SoftwareUnit(softUnitDb.getId(), softUnitDb.getName(),
				softUnitDb.getFullName(), softUnitDb.getUid(), softUnitDb.getType());
	}

	/**
	 * @param file
	 * @param softwareUnit
	 * @param value
	 * @return Software unit saved.
	 * Saves the software unit and its metric value in database.
	 * See saveSoftwareUnit(File file, SoftwareUnit softwareUnit) method documentation to understand how save works.
	 * If the software unit is not trailer with a file, as package, for example, pass file as null.
	 * Use this method if you want save the software unit with its metric value.
	 */
	public SoftwareUnit saveMetricValue(File file, SoftwareUnit softwareUnit, String value){

		SoftwareUnit softUnit = saveSoftwareUnit(file, softwareUnit);
		MetricValuePK metricValPk = new MetricValuePK(softUnit.getId(), commit.getId(), metric.getId());
		MetricValueDB metricValDb =  new MetricValueDB(metricValPk, value);

		metricValueDao.merge(metricValDb);

		return softUnit;

	}

	/**
	 * @param file
	 * @param parent
	 * @param fullName
	 * @param type
	 * @return Software unit.
	 * Finds some software unit by its unique id.
	 * If the software unit is not trailer with a file, as package, for example, pass file as null.
	 */
	public SoftwareUnit getSoftwareUnit(File file, SoftwareUnit parent, String fullName, SoftwareUnitType type){

		String uid = generateUid(file, parent, fullName, type);
		SoftwareUnitDB softUnitDb = softUnitDao.findByUid(uid);

		if(softUnitDb == null){
			return null;
		}

		return new SoftwareUnit(softUnitDb.getId(), softUnitDb.getName(),
				softUnitDb.getFullName(), softUnitDb.getUid(), softUnitDb.getType());

	}

	/**
	 * @param metricUid
	 * @param softUnitId
	 * @param commitPrev
	 * @return The value of a certain metric calculated over software unit.
	 * Pass commitPrev as parameter if you want get the value of the metric for the software unit in the previous commit.
	 * If you want the value of the metric calculated for the software unit in the current commit pass null as parameter.
	 */
	public String getMetricValue(MetricUid metricUid, int softUnitId, Commit commit){

		MetricValuePK pk = new MetricValuePK(softUnitId, commit.getId(), metricUid.getId());
		MetricValueDB metricValDb = metricValueDao.find(pk);

		if(metricValDb == null){
			return null;
		}

		return metricValDb.getValue();

	}


	// helpers

	// generates unique id for software units
	private String generateUid(File file, SoftwareUnit parent, String fullName, SoftwareUnitType type){

		String filePath = null;
		if(file != null){
			filePath = file.getPath();
		}

		String parentPath = null;
		if(parent != null){
			parentPath = parent.getUid();
		}

		String uid = null;
		if(type == SoftwareUnitType.PROJECT || type == SoftwareUnitType.PACKAGE
				|| type == SoftwareUnitType.FILE){
			uid = repositoryPath+"/"+parentPath+"/"+filePath+"/"+type.toString()+"/"+fullName;
		}else{
			uid = repositoryPath+"/"+filePath+"/"+parentPath+"/"+type.toString()+"/"+fullName;
		}

		return StringUtils.sha1(uid);

	}

}