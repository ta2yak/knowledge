package org.support.project.knowledge.control.admin;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.support.project.common.bean.ValidateError;
import org.support.project.common.logic.H2DBServerLogic;
import org.support.project.common.serialize.SerializeUtils;
import org.support.project.common.wrapper.FileInputStreamWithDeleteWrapper;
import org.support.project.di.Container;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.config.SystemConfig;
import org.support.project.knowledge.control.Control;
import org.support.project.knowledge.deploy.InitDB;
import org.support.project.knowledge.deploy.InitializationLogic;
import org.support.project.knowledge.logic.DataTransferLogic;
import org.support.project.knowledge.logic.DatabaseLogic;
import org.support.project.ormapping.config.ConnectionConfig;
import org.support.project.ormapping.config.ConnectionConfigLoader;
import org.support.project.ormapping.config.ORMappingParameter;
import org.support.project.ormapping.connection.ConnectionManager;
import org.support.project.ormapping.exception.ORMappingException;
import org.support.project.ormapping.transaction.TransactionManager;
import org.support.project.web.annotation.Auth;
import org.support.project.web.boundary.Boundary;
import org.support.project.web.dao.SystemConfigsDao;
import org.support.project.web.entity.SystemConfigsEntity;
import org.support.project.web.logic.DBConnenctionLogic;

public class DatabaseControl extends Control {
	
	/* (non-Javadoc)
	 * @see org.support.project.web.control.Control#index()
	 */
	@Override
	public Boundary index() {
		H2DBServerLogic h2dbServerLogic = H2DBServerLogic.get();
		boolean active = h2dbServerLogic.isActive();
		setAttribute("active", active);
		return super.index();
	}
	
	/**
	 * 組み込みDBを開始
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary start() {
		H2DBServerLogic h2dbServerLogic = H2DBServerLogic.get();
		h2dbServerLogic.start();
		boolean active = h2dbServerLogic.isActive();
		setAttribute("active", active);
		
		// 組み込みDBを使っている場合、コネクション接続
		if (DBConnenctionLogic.get().getCustomConnectionConfig() == null) {
			//現在カスタム設定でない
			ConnectionManager.getInstance().addConnectionConfig(DBConnenctionLogic.get().getDefaultConnectionConfig());
			InitializationLogic.get().init();
		}
		
		return super.index();
	}
	
	
	/**
	 * 組み込みDBを停止
	 * @return
	 * @throws ORMappingException
	 * @throws SQLException
	 */
	@Auth(roles="admin")
	public Boundary stop() throws ORMappingException, SQLException {
		// 組み込みDBを使っている場合、コネクション解除
		if (DBConnenctionLogic.get().getCustomConnectionConfig() == null) {
			//現在カスタム設定でない
			TransactionManager transactionManager = Container.getComp(TransactionManager.class);
			transactionManager.release(ConnectionManager.getInstance().getDefaultConnectionName());
			ConnectionManager.getInstance().release();
		}
		
		H2DBServerLogic h2dbServerLogic = H2DBServerLogic.get();
		h2dbServerLogic.stop();
		boolean active = h2dbServerLogic.isActive();
		setAttribute("active", active);
		return super.index();
	}

	
	
	/**
	 * データをバックアップ
	 * @return
	 * @throws IOException 
	 */
	@Auth(roles="admin")
	public Boundary backup() throws IOException {
		
		HttpServletResponse res = getResponse();
		res.setDateHeader("Expires",0);
		res.setHeader("Pragma","no-cache");
		res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
		
		DatabaseLogic databaseLogic = DatabaseLogic.get();
		FileInputStreamWithDeleteWrapper inputStream = databaseLogic.getData();
		
		return download("knowledge.zip", inputStream, inputStream.size(), "application/zip");
	}

	/**
	 * 組み込みDBのデータをバックアップから復元
	 * @return
	 * @throws IOException
	 */
	@Auth(roles="admin")
	public Boundary restore() throws IOException {
		H2DBServerLogic h2dbServerLogic = H2DBServerLogic.get();
		boolean active = h2dbServerLogic.isActive();
		if (active) {
			addMsgInfo("knowledge.data.label.msg.before.stop");
			setAttribute("active", active);
			return super.index();
		}
		
		FileItem fileItem = super.getParam("upload", FileItem.class);
		if (fileItem == null || fileItem.getSize() == 0) {
			addMsgWarn("knowledge.data.label.msg.empty");
		} else if (!fileItem.getName().endsWith(".zip") && !fileItem.getName().endsWith(".ZIP")) {
			addMsgWarn("knowledge.data.label.msg.invalid.file");
		} else {
			DatabaseLogic databaseLogic = DatabaseLogic.get();
			List<ValidateError> errors = databaseLogic.restore(fileItem);
			setResult("knowledge.data.label.msg.restore", errors);
		}
		active = h2dbServerLogic.isActive();
		setAttribute("active", active);
		return super.index();
	}
	
	/**
	 * データベースに接続
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary connect() {
		ConnectionConfig connectionConfig = DBConnenctionLogic.get().getCustomConnectionConfig();
		if (connectionConfig == null) {
			//ConnectionConfigLoader loader = Container.getComp("XML", ConnectionConfigLoader.class);
			//connectionConfig = loader.load(ORMappingParameter.CONNECTION_SETTING);
			connectionConfig = new ConnectionConfig();
			setAttribute("custom", Boolean.FALSE);
		} else {
			setAttribute("custom", Boolean.TRUE);
			if (DataTransferLogic.get().isTransferRequested()) {
				setAttribute("transfer", Boolean.TRUE);
			} else {
				setAttribute("transfer", Boolean.FALSE);
				ConnectionManager.getInstance().addConnectionConfig(connectionConfig); //新しいコネクション設定をセット
			}
		}
		
		setAttributeOnProperty(connectionConfig);
		return forward("connection.jsp");
	}
	
	/**
	 * コネクションのカスタム設定を保存
	 * @return
	 * @throws Exception
	 */
	@Auth(roles="admin")
	public Boundary custom_save() throws Exception {
		ConnectionConfig connectionConfig = super.getParamOnProperty(ConnectionConfig.class);
		if (!ConnectionManager.getInstance().checkConnectionConfig(connectionConfig)) {
			// 入力したコネクション設定が無効
			addMsgWarn("errors.invalid", getResource("knowledge.connection.label.custom"));
			return forward("connection.jsp");
		}
		// カスタム設定
		connectionConfig.setName("custom");
		ConnectionManager.getInstance().removeDefaultConnectionConfig(); // 現在有効なコネクションの設定を削除
		ConnectionManager.getInstance().addConnectionConfig(connectionConfig); //新しいコネクション設定をセット
		
		// 新しい接続先のDBを初期化
		InitDB initDB = new InitDB();
		initDB.start();
		
		// 設定ファイルを保存
		OutputStream out = null;
		try {
			out = new FileOutputStream(DBConnenctionLogic.get().getCustomConnectionConfigPath());
			SerializeUtils.writeObject(connectionConfig, out);
		} finally {
			if (out != null) {
				out.close();
			}
		}
		addMsgSuccess("message.success.save.target", getResource("knowledge.connection.label.custom"));
		return connect();
	}
	
	/**
	 * コネクションのカスタム設定を削除
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary custom_delete() {
		ConnectionConfig connectionConfig = DBConnenctionLogic.get().getCustomConnectionConfig();
		if (connectionConfig != null) {
			ConnectionManager.getInstance().removeConnectionConfig(connectionConfig);
			DBConnenctionLogic.get().removeCustomConnectionConfig();
		}
		
		// カスタム設定を削除したので、デフォルトを切り替え
		ConnectionConfigLoader loader = Container.getComp("XML", ConnectionConfigLoader.class);
		connectionConfig = loader.load(ORMappingParameter.CONNECTION_SETTING);
		// 設定が正しく読み込めれば、それを使う
		ConnectionManager.getInstance().addConnectionConfig(connectionConfig);
		
		addMsgSuccess("message.success.delete.target", getResource("knowledge.connection.label.custom"));
		
		return connect();
	}

	/**
	 * データ移行のリクエストをうける
	 * @return
	 * @throws IOException
	 */
	@Auth(roles="admin")
	public Boundary data_transfer() throws IOException {
		DataTransferLogic.get().requestTransfer();
		addMsgSuccess("knowledge.connection.msg.custom.transfer.request");
		return connect();
	}
	
	
	/**
	 * インデックス再生性のページを表示
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary reindexing() {
		SystemConfigsEntity entity = SystemConfigsDao.get().selectOnKey(SystemConfig.RE_INDEXING, AppConfig.SYSTEM_NAME);
		if (entity != null) {
			setAttribute("start_reindexing", Boolean.TRUE);
		} else {
			setAttribute("start_reindexing", Boolean.FALSE);
		}
		return forward("reindexing.jsp");
	}
	
	/**
	 * インデックス再生性のリクエストをうける
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary start_reindexing() {
		SystemConfigsEntity entity = SystemConfigsDao.get().selectOnKey(SystemConfig.RE_INDEXING, AppConfig.SYSTEM_NAME);
		if (entity != null) {
			addMsgInfo("message.allready.started");
			return reindexing();
		}

		Long start = getParam("start", Long.class);
		Long end = getParam("end", Long.class);
		String val = "start=" + start + ",end=" + end;
		
		entity = new SystemConfigsEntity();
		entity.setSystemName(AppConfig.SYSTEM_NAME);
		entity.setConfigName(SystemConfig.RE_INDEXING);
		entity.setConfigValue(val);
		
		SystemConfigsDao.get().save(entity);
		
		return reindexing();
	}
	
	
}
