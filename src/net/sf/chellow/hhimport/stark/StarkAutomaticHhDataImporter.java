package net.sf.chellow.hhimport.stark;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

import net.sf.chellow.billing.Contract;
import net.sf.chellow.billing.HhdcContract;
import net.sf.chellow.hhimport.HhDataImportProcess;
import net.sf.chellow.monad.HttpException;
import net.sf.chellow.monad.InternalException;
import net.sf.chellow.monad.Invocation;
import net.sf.chellow.monad.MonadMessage;
import net.sf.chellow.monad.MonadUtils;
import net.sf.chellow.monad.OkException;
import net.sf.chellow.monad.Urlable;
import net.sf.chellow.monad.UserException;
import net.sf.chellow.monad.XmlDescriber;
import net.sf.chellow.monad.XmlTree;
import net.sf.chellow.monad.types.MonadDate;
import net.sf.chellow.monad.types.MonadUri;
import net.sf.chellow.monad.types.UriPathElement;
import net.sf.chellow.ui.ChellowLogger;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class StarkAutomaticHhDataImporter implements Urlable, XmlDescriber {
	static public final UriPathElement URI_ID;

	static {
		try {
			URI_ID = new UriPathElement("stark-automatic-hh-data-importer");
		} catch (HttpException e) {
			throw new RuntimeException(e);
		}
	}

	private HhDataImportProcess hhImporter;

	private List<MonadMessage> messages = Collections
			.synchronizedList(new ArrayList<MonadMessage>());

	private Long contractId;

	// private Properties state;

	public StarkAutomaticHhDataImporter(HhdcContract contract)
			throws HttpException {
		contractId = contract.getId();
	}

	private void log(String message) throws HttpException {
		messages.add(new MonadMessage(new MonadDate().toString() + ": "
				+ message));
		if (messages.size() > 200) {
			messages.remove(0);
		}
	}

	private String getPropertyNameLastImportDate(int directory) {
		return "lastImportDate" + directory;
	}

	private String getPropertyNameLastImportName(int directory) {
		return "lastImportName" + directory;
	}

	public void run() {
		FTPClient ftp = null;
		try {
			HhdcContract contract = HhdcContract.getHhdcContract(contractId);
			/*
			 * state = new Properties(); try { state.load(new
			 * StringReader(contract.getState())); } catch (IOException e) {
			 * throw new InternalException(e); }
			 */
			String hostName = contract.getProperty("hostname");
			String userName = contract.getProperty("username");
			String password = contract.getProperty("password");
			List<String> directories = new ArrayList<String>();
			String directory = null;
			for (int i = 0; (directory = contract.getProperty("directory" + i)) != null; i++) {
				directories.add(directory);
			}

			ftp = new FTPClient();
			int reply;
			ftp.connect(hostName);
			log("Connecting to ftp server at " + hostName + ".");
			log("Server replied with '" + ftp.getReplyString() + "'.");
			reply = ftp.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				throw new UserException("FTP server refused connection.");
			}
			log("Connected to server, now logging in.");
			ftp.login(userName, password);
			log("Server replied with '" + ftp.getReplyString() + "'.");
			for (int i = 0; i < directories.size(); i++) {
				log("Checking the directory '" + directories.get(i) + "'.");
				boolean found = false;
				FTPFile[] filesArray = ftp.listFiles(directories.get(i));
				List<FTPFile> files = Arrays.asList(filesArray);
				Collections.sort(files, new Comparator<FTPFile>() {
					public int compare(FTPFile file1, FTPFile file2) {
						if (file2.getTimestamp().getTime().equals(
								file1.getTimestamp().getTime())) {
							return file2.getName().compareTo(file1.getName());
						} else {
							return file1.getTimestamp().getTime().compareTo(
									file2.getTimestamp().getTime());
						}
					}
				});
				String lastImportDateStr = contract
						.getStateProperty(getPropertyNameLastImportDate(i));
				Date lastImportDate = null;
				if (lastImportDateStr != null
						&& lastImportDateStr.length() != 0) {
					lastImportDate = new MonadDate(lastImportDateStr).getDate();
				}
				String lastImportName = contract
						.getProperty(getPropertyNameLastImportName(i));
				for (FTPFile file : files) {
					if (file.getType() == FTPFile.FILE_TYPE
							&& (lastImportDate == null ? true : (file
									.getTimestamp().getTime().equals(
											lastImportDate) ? file.getName()
									.compareTo(lastImportName) < 0 : file
									.getTimestamp().getTime().after(
											lastImportDate)))) {
						String fileName = directories.get(i) + "\\"
								+ file.getName();
						if (file.getSize() == 0) {
							log("Ignoring '" + fileName
									+ "'because it has zero length");
						} else {
							log("Attempting to download '" + fileName + "'.");
							InputStream is = ftp.retrieveFileStream(fileName);
							if (is == null) {
								reply = ftp.getReplyCode();
								throw new UserException(
										"Can't download the file '"
												+ file.getName()
												+ "', server says: " + reply
												+ ".");
							}
							log("File stream obtained successfully.");
							hhImporter = new HhDataImportProcess(getContract()
									.getId(), new Long(0), is, fileName
									+ ".df2", file.getSize());
							hhImporter.run();

							List<String> messages = hhImporter.getMessages();
							hhImporter = null;
							if (messages.size() > 0) {
								for (String message : messages) {
									log(message);
								}
								throw new UserException("Problem loading file.");
							}
						}
						if (!ftp.completePendingCommand()) {
							throw new OkException(
									"Couldn't complete ftp transaction: "
											+ ftp.getReplyString());
						}
						contract.setStateProperty(
								getPropertyNameLastImportDate(i),
								new MonadDate(lastImportDate).toString());
						contract.setStateProperty(
								getPropertyNameLastImportName(i), file
										.getName());
					}
				}
				if (!found) {
					log("No new files found.");
				}
			}
		} catch (HttpException e) {
			try {
				log(e.getMessage());
			} catch (InternalException e1) {
				throw new RuntimeException(e1);
			} catch (HttpException e1) {
				throw new RuntimeException(e1);
			}
		} catch (IOException e) {
			try {
				log(e.getMessage());
			} catch (InternalException e1) {
				throw new RuntimeException(e1);
			} catch (HttpException e1) {
				throw new RuntimeException(e1);
			}
		} catch (Throwable e) {
			try {
				log("Exception: " + e.getClass().getName() + " Message: "
						+ e.getMessage());
			} catch (InternalException e1) {
				throw new RuntimeException(e1);
			} catch (HttpException e1) {
				throw new RuntimeException(e1);
			}
			ChellowLogger.getLogger().logp(Level.SEVERE, "ContextListener",
					"contextInitialized", "Can't initialize context.", e);
		} finally {
			if (ftp != null && ftp.isConnected()) {
				try {
					ftp.logout();
					ftp.disconnect();
					log("Logged out.");
				} catch (IOException ioe) {
					// do nothing
				} catch (InternalException e) {
					// do nothing
				} catch (HttpException e) {
					// do nothing
				}
			}
		}
	}

	public List<MonadMessage> getLog() {
		return messages;
	}

	public void httpGet(Invocation inv) throws HttpException {
		Document doc = MonadUtils.newSourceDocument();
		Element source = doc.getDocumentElement();
		Element importerElement = toXml(doc);
		source.appendChild(importerElement);
		importerElement.appendChild(getContract().toXml(doc,
				new XmlTree("party")));
		for (MonadMessage message : getLog()) {
			importerElement.appendChild(message.toXml(doc));
		}
		if (hhImporter != null) {
			importerElement.appendChild(new MonadMessage(hhImporter.status())
					.toXml(doc));
		}
		inv.sendOk(doc);
	}

	public UriPathElement getUriId() {
		return URI_ID;
	}

	public void httpPost(Invocation inv) throws InternalException,
			HttpException {
		// TODO Auto-generated method stub

	}

	public void httpDelete(Invocation inv) throws InternalException,
			HttpException {
		// TODO Auto-generated method stub

	}

	public Urlable getChild(UriPathElement uriId) throws InternalException,
			HttpException {
		// TODO Auto-generated method stub
		return null;
	}

	private Contract getContract() throws HttpException {
		return Contract.getContract(contractId);
	}

	public MonadUri getUri() throws InternalException, HttpException {
		return getContract().getUri().resolve(getUriId()).append("/");
	}

	public Element toXml(Document doc) throws InternalException, HttpException {
		return doc.createElement("stark-automatic-hh-data-importer");
	}

	public Node toXml(Document doc, XmlTree tree) throws HttpException {
		// TODO Auto-generated method stub
		return null;
	}
}