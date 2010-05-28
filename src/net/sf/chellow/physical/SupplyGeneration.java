/*******************************************************************************
 * 
 *  Copyright (c) 2005, 2009 Wessex Water Services Limited
 *  
 *  This file is part of Chellow.
 * 
 *  Chellow is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  Chellow is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with Chellow.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *******************************************************************************/

package net.sf.chellow.physical;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.chellow.billing.Bill;
import net.sf.chellow.billing.Dso;
import net.sf.chellow.billing.HhdcContract;
import net.sf.chellow.billing.MopContract;
import net.sf.chellow.billing.SupplierContract;
import net.sf.chellow.monad.Debug;
import net.sf.chellow.monad.Hiber;
import net.sf.chellow.monad.HttpException;
import net.sf.chellow.monad.Invocation;
import net.sf.chellow.monad.MonadUtils;
import net.sf.chellow.monad.NotFoundException;
import net.sf.chellow.monad.Urlable;
import net.sf.chellow.monad.UserException;
import net.sf.chellow.monad.XmlTree;
import net.sf.chellow.monad.types.MonadDate;
import net.sf.chellow.monad.types.MonadUri;
import net.sf.chellow.monad.types.UriPathElement;
import net.sf.chellow.ui.GeneralImport;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.exception.ConstraintViolationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SupplyGeneration extends PersistentEntity {
	static public void generalImport(String action, String[] values,
			Element csvElement) throws HttpException {
		if (action.equals("update")) {
			String mpanCoreStr = GeneralImport.addField(csvElement,
					"MPAN Core", values, 0);
			MpanCore mpanCore = MpanCore.getMpanCore(mpanCoreStr);
			Supply supply = mpanCore.getSupply();
			String dateStr = GeneralImport.addField(csvElement, "Date", values,
					1);

			SupplyGeneration supplyGeneration = supply.getGeneration(dateStr
					.length() == 0 ? null : new HhStartDate(dateStr));
			if (supplyGeneration == null) {
				throw new UserException(
						"There isn't a generation at this date.");
			}
			String startDateStr = GeneralImport.addField(csvElement,
					"Start date", values, 2);
			String finishDateStr = GeneralImport.addField(csvElement,
					"Finish date", values, 3);
			MopContract mopContract = null;
			String mopContractName = GeneralImport.addField(csvElement,
					"MOP Contract", values, 4);
			if (mopContractName.equals(GeneralImport.NO_CHANGE)) {
				mopContract = supplyGeneration.getMopContract();
			} else if (mopContractName.length() > 0) {
				mopContract = MopContract.getMopContract(mopContractName);
			}
			String mopAccount = GeneralImport.addField(csvElement,
					"MOP Account", values, 5);
			if (mopAccount.equals(GeneralImport.NO_CHANGE)) {
				mopAccount = supplyGeneration.getMopAccount();
			}
			HhdcContract hhdcContract = null;
			String hhdcContractName = GeneralImport.addField(csvElement,
					"HHDC Contract", values, 6);
			if (hhdcContractName.equals(GeneralImport.NO_CHANGE)) {
				hhdcContract = supplyGeneration.getHhdcContract();
			} else if (hhdcContractName.length() > 0) {
				hhdcContract = HhdcContract.getHhdcContract(hhdcContractName);
			}
			String hhdcAccount = GeneralImport.addField(csvElement,
					"HHDC account", values, 7);
			if (hhdcAccount.equals(GeneralImport.NO_CHANGE)) {
				hhdcAccount = supplyGeneration.getHhdcAccount();
			}
			String hasImportKwhStr = GeneralImport.addField(csvElement,
					"Has HH import kWh?", values, 8);
			boolean hasImportKwh = hasImportKwhStr
					.equals(GeneralImport.NO_CHANGE) ? supplyGeneration
					.getChannel(true, true) != null : Boolean
					.parseBoolean(hasImportKwhStr);
			String hasImportKvarhStr = GeneralImport.addField(csvElement,
					"Has HH import kVArh?", values, 9);
			boolean hasImportKvarh = hasImportKvarhStr
					.equals(GeneralImport.NO_CHANGE) ? supplyGeneration
					.getChannel(true, false) != null : Boolean
					.parseBoolean(hasImportKvarhStr);
			String hasExportKwhStr = GeneralImport.addField(csvElement,
					"Has HH export kWh?", values, 10);
			boolean hasExportKwh = hasExportKwhStr
					.equals(GeneralImport.NO_CHANGE) ? supplyGeneration
					.getChannel(false, true) != null : Boolean
					.parseBoolean(hasExportKwhStr);
			String hasExportKvarhStr = GeneralImport.addField(csvElement,
					"Has HH export kVArh?", values, 11);
			boolean hasExportKvarh = hasExportKvarhStr
					.equals(GeneralImport.NO_CHANGE) ? supplyGeneration
					.getChannel(false, false) != null : Boolean
					.parseBoolean(hasExportKvarhStr);
			for (boolean isImport : new boolean[] { true, false }) {
				for (boolean isKwh : new boolean[] { true, false }) {
					boolean hasChannel;
					if (isImport) {
						if (isKwh) {
							hasChannel = hasImportKwh;
						} else {
							hasChannel = hasImportKvarh;
						}
					} else {
						if (isKwh) {
							hasChannel = hasExportKwh;
						} else {
							hasChannel = hasExportKvarh;
						}
					}
					if (hasChannel
							&& supplyGeneration.getChannel(isImport, isKwh) == null) {
						supplyGeneration.insertChannel(isImport, isKwh);
					}
					if (!hasChannel
							&& supplyGeneration.getChannel(isImport, isKwh) != null) {
						supplyGeneration.deleteChannel(isImport, isKwh);
					}
				}
			}
			String importMpanStr = GeneralImport.addField(csvElement,
					"Import MPAN", values, 12);
			Ssc importSsc = null;
			Integer importAgreedSupplyCapacity = null;
			SupplierContract importSupplierContract = null;
			String importSupplierAccount = null;
			Mpan existingImportMpan = supplyGeneration.getImportMpan();
			if (importMpanStr.equals(GeneralImport.NO_CHANGE)) {
				importMpanStr = existingImportMpan == null ? null
						: existingImportMpan.toString();
			} else if (importMpanStr.trim().length() == 0) {
				importMpanStr = null;
			}
			if (importMpanStr != null) {
				String importSscCode = GeneralImport.addField(csvElement,
						"Import SSC", values, 13);
				if (importSscCode.equals(GeneralImport.NO_CHANGE)) {
					if (existingImportMpan == null) {
						throw new UserException(
								"There isn't an existing import MPAN.");
					} else {
						importSsc = existingImportMpan.getSsc();
					}
				} else {
					importSsc = importSscCode.length() == 0 ? null : Ssc
							.getSsc(importSscCode);
				}
				String importAgreedSupplyCapacityStr = GeneralImport
						.addField(csvElement, "Import Agreed Supply Capacity",
								values, 14);
				if (importAgreedSupplyCapacityStr
						.equals(GeneralImport.NO_CHANGE)) {
					if (existingImportMpan == null) {
						throw new UserException(
								"There isn't an existing import MPAN.");
					} else {
						importAgreedSupplyCapacity = existingImportMpan
								.getAgreedSupplyCapacity();
					}
				} else {
					try {
						importAgreedSupplyCapacity = Integer
								.parseInt(importAgreedSupplyCapacityStr);
					} catch (NumberFormatException e) {
						throw new UserException(
								"The import agreed supply capacity must be an integer. "
										+ e.getMessage());
					}
				}
				String importSupplierContractName = GeneralImport.addField(
						csvElement, "Import Supplier Contract", values, 15);
				if (importSupplierContractName.equals(GeneralImport.NO_CHANGE)) {
					if (existingImportMpan == null) {
						throw new UserException(
								"There isn't an existing import supplier.");
					}
					importSupplierContract = existingImportMpan
							.getSupplierContract();
				} else {
					importSupplierContract = SupplierContract
							.getSupplierContract(importSupplierContractName);
				}
				importSupplierAccount = GeneralImport.addField(csvElement,
						"Import Supplier Account", values, 16);
				if (importSupplierAccount.equals(GeneralImport.NO_CHANGE)) {
					if (existingImportMpan == null) {
						throw new UserException(
								"There isn't an existing import supplier account.");
					}
					importSupplierAccount = existingImportMpan
							.getSupplierAccount();
				}
			}
			String exportMpanStr = null;
			Ssc exportSsc = null;
			SupplierContract exportSupplierContract = null;
			String exportSupplierAccount = null;
			Integer exportAgreedSupplyCapacity = null;
			if (values.length > 17) {
				exportMpanStr = GeneralImport.addField(csvElement,
						"Eport MPAN", values, 17);
				Mpan existingExportMpan = supplyGeneration.getExportMpan();
				if (exportMpanStr.equals(GeneralImport.NO_CHANGE)) {
					exportMpanStr = existingExportMpan == null ? null
							: existingExportMpan.toString();
				} else if (exportMpanStr.trim().length() == 0) {
					exportMpanStr = null;
				}
				if (exportMpanStr != null) {
					String exportSscCode = GeneralImport.addField(csvElement,
							"Export SSC", values, 18);
					if (exportSscCode.equals(GeneralImport.NO_CHANGE)) {
						if (existingExportMpan == null) {
							throw new UserException(
									"There isn't an existing export MPAN.");
						} else {
							exportSsc = existingExportMpan.getSsc();
						}
					} else {
						exportSsc = exportSscCode.length() == 0 ? null : Ssc
								.getSsc(exportSscCode);
					}
					String exportAgreedSupplyCapacityStr = GeneralImport
							.addField(csvElement,
									"Export Agreed Supply Capacity", values, 19);
					if (exportAgreedSupplyCapacityStr
							.equals(GeneralImport.NO_CHANGE)) {
						if (existingExportMpan == null) {
							throw new UserException(
									"There isn't an existing export MPAN.");
						} else {
							exportAgreedSupplyCapacity = existingExportMpan
									.getAgreedSupplyCapacity();
						}
					} else {
						try {
							exportAgreedSupplyCapacity = new Integer(
									exportAgreedSupplyCapacityStr);
						} catch (NumberFormatException e) {
							throw new UserException(
									"The export supply capacity must be an integer. "
											+ e.getMessage());
						}
					}
					String exportSupplierContractName = GeneralImport.addField(
							csvElement, "Export Supplier Contract", values, 20);
					if (exportSupplierContractName
							.equals(GeneralImport.NO_CHANGE)) {
						if (existingExportMpan == null) {
							throw new UserException(
									"There isn't an existing export supplier contract.");
						}
						exportSupplierContract = existingExportMpan
								.getSupplierContract();
					} else {
						exportSupplierContract = SupplierContract
								.getSupplierContract(exportSupplierContractName);
					}
					exportSupplierAccount = GeneralImport.addField(csvElement,
							"Export Supplier Account", values, 21);
					if (exportSupplierAccount.equals(GeneralImport.NO_CHANGE)) {
						if (existingExportMpan == null) {
							throw new UserException(
									"There isn't an existing export MPAN.");
						}
						exportSupplierAccount = existingExportMpan
								.getSupplierAccount();
					}
				}
			}
			supply.updateGeneration(supplyGeneration, startDateStr
					.equals(GeneralImport.NO_CHANGE) ? supplyGeneration
					.getStartDate() : new HhStartDate("start", startDateStr),
					finishDateStr.length() == 0 ? null : (finishDateStr
							.equals(GeneralImport.NO_CHANGE) ? supplyGeneration
							.getFinishDate() : new HhStartDate("finish",
							finishDateStr)));
			supplyGeneration.update(supplyGeneration.getStartDate(),
					supplyGeneration.getFinishDate(), mopContract, mopAccount,
					hhdcContract, hhdcAccount, importMpanStr, importSsc,
					importSupplierContract, importSupplierAccount,
					importAgreedSupplyCapacity, exportMpanStr, exportSsc,
					exportSupplierContract, exportSupplierAccount,
					exportAgreedSupplyCapacity);
		} else if (action.equals("delete")) {
			String mpanCoreStr = GeneralImport.addField(csvElement,
					"MPAN Core", values, 0);
			MpanCore mpanCore = MpanCore.getMpanCore(mpanCoreStr);
			Supply supply = mpanCore.getSupply();
			String dateStr = GeneralImport.addField(csvElement, "Date", values,
					1);

			SupplyGeneration supplyGeneration = supply.getGeneration(dateStr
					.length() == 0 ? null : new HhStartDate(dateStr));
			if (supplyGeneration == null) {
				throw new UserException(
						"There isn't a generation at this date.");
			}
			supply.deleteGeneration(supplyGeneration);
		} else if (action.equals("insert")) {
			String siteCode = GeneralImport.addField(csvElement, "Site Code",
					values, 0);
			Site site = Site.getSite(siteCode);
			Supply supply = null;
			String startDateStr = GeneralImport.addField(csvElement,
					"Start date", values, 1);
			HhStartDate startDate = startDateStr.length() == 0 ? null
					: new HhStartDate(startDateStr);
			String mopContractName = GeneralImport.addField(csvElement,
					"MOP Contract", values, 2);
			MopContract mopContract = null;
			if (mopContractName.length() > 0) {
				mopContract = MopContract.getMopContract(mopContractName);
			}
			String mopAccount = GeneralImport.addField(csvElement,
					"MOP Account Reference", values, 3);
			String hhdcContractName = GeneralImport.addField(csvElement,
					"HHDC Contract", values, 4);
			HhdcContract hhdcContract = null;
			if (hhdcContractName.length() > 0) {
				hhdcContract = HhdcContract.getHhdcContract(hhdcContractName);
			}
			String hhdcAccount = GeneralImport.addField(csvElement,
					"HHDC Account Reference", values, 5);
			String hasImportKwhStr = GeneralImport.addField(csvElement,
					"Has HH import kWh", values, 6);
			boolean hasImportKwh = Boolean.parseBoolean(hasImportKwhStr);
			String hasImportKvarhStr = GeneralImport.addField(csvElement,
					"Has HH import kVArh", values, 7);
			boolean hasImportKvarh = Boolean.parseBoolean(hasImportKvarhStr);
			String hasExportKwhStr = GeneralImport.addField(csvElement,
					"Has HH export kWh", values, 8);
			Boolean hasExportKwh = Boolean.parseBoolean(hasExportKwhStr);
			String hasExportKvarhStr = GeneralImport.addField(csvElement,
					"Has HH export kVArh", values, 9);
			Boolean hasExportKvarh = Boolean.parseBoolean(hasExportKvarhStr);
			String meterSerialNumber = GeneralImport.addField(csvElement,
					"Meter Serial Number", values, 10);
			String importMpanStr = GeneralImport.addField(csvElement,
					"Import MPAN", values, 11);
			SupplierContract importSupplierContract = null;
			String importSupplierAccountReference = null;
			Ssc importSsc = null;
			Integer importAgreedSupplyCapacity = null;
			String importSscCode = GeneralImport.addField(csvElement,
					"Import SSC", values, 12);
			String importAgreedSupplyCapacityStr = GeneralImport.addField(
					csvElement, "Import Agreed Supply Capacity", values, 13);
			String importContractSupplierName = GeneralImport.addField(
					csvElement, "Import Supplier Contract", values, 14);
			importSupplierAccountReference = GeneralImport.addField(csvElement,
					"Import Supplier Account Reference", values, 15);
			if (importMpanStr.length() > 0) {
				MpanCore mpanCore = MpanCore.findMpanCore(Mpan
						.getCore(importMpanStr));
				if (mpanCore != null) {
					supply = mpanCore.getSupply();
				}
				importSsc = importSscCode.length() == 0 ? null : Ssc
						.getSsc(importSscCode);
				try {
					importAgreedSupplyCapacity = Integer
							.parseInt(importAgreedSupplyCapacityStr);
				} catch (NumberFormatException e) {
					throw new UserException(
							"The import agreed supply capacity must be an integer. "
									+ e.getMessage());
				}
				importSupplierContract = SupplierContract
						.getSupplierContract(importContractSupplierName);
			}

			String exportMpanStr = null;
			Ssc exportSsc = null;
			SupplierContract exportSupplierContract = null;
			String exportSupplierAccountReference = null;
			Integer exportAgreedSupplyCapacity = null;

			if (values.length > 1) {
				exportMpanStr = GeneralImport.addField(csvElement,
						"Eport MPAN", values, 16);

				if (exportMpanStr.length() > 0) {
					String exportSscCode = GeneralImport.addField(csvElement,
							"Export SSC", values, 17);
					String exportAgreedSupplyCapacityStr = GeneralImport
							.addField(csvElement,
									"Export Agreed Supply Capacity", values, 18);
					String exportContractSupplierName = GeneralImport.addField(
							csvElement, "Export Supplier Contract", values, 19);
					exportSupplierAccountReference = GeneralImport.addField(
							csvElement, "Export Supplier Account", values, 20);
					if (supply == null) {
						supply = MpanCore.getMpanCore(
								Mpan.getCore(exportMpanStr)).getSupply();
					}
					exportSsc = exportSscCode.length() == 0 ? null : Ssc
							.getSsc(exportSscCode);
					try {
						exportAgreedSupplyCapacity = new Integer(
								exportAgreedSupplyCapacityStr);
					} catch (NumberFormatException e) {
						throw new UserException(
								"The export supply capacity must be an integer. "
										+ e.getMessage());
					}
					exportSupplierContract = SupplierContract
							.getSupplierContract(exportContractSupplierName);
				}
			}
			Map<Site, Boolean> siteMap = new HashMap<Site, Boolean>();
			siteMap.put(site, true);
			SupplyGeneration generation = supply.insertGeneration(siteMap,
					startDate, mopContract, mopAccount, hhdcContract,
					hhdcAccount, meterSerialNumber, importMpanStr, importSsc,
					importSupplierContract, importSupplierAccountReference,
					importAgreedSupplyCapacity, exportMpanStr, exportSsc,
					exportSupplierContract, exportSupplierAccountReference,
					exportAgreedSupplyCapacity);
			for (boolean isImport : new boolean[] { true, false }) {
				for (boolean isKwh : new boolean[] { true, false }) {
					boolean hasChannel;
					if (isImport) {
						if (isKwh) {
							hasChannel = hasImportKwh;
						} else {
							hasChannel = hasImportKvarh;
						}
					} else {
						if (isKwh) {
							hasChannel = hasExportKwh;
						} else {
							hasChannel = hasExportKvarh;
						}
					}
					Channel channel = generation.getChannel(isImport, isKwh);
					if (hasChannel && channel == null) {
						generation.insertChannel(isImport, isKwh);
					} else if (!hasChannel && channel != null) {
						generation.deleteChannel(isImport, isKwh);
					}
				}
			}
		}
	}

	static public SupplyGeneration getSupplyGeneration(Long id)
			throws HttpException {
		SupplyGeneration supplyGeneration = (SupplyGeneration) Hiber.session()
				.get(SupplyGeneration.class, id);
		if (supplyGeneration == null) {
			throw new UserException(
					"There is no supply generation with that id.");
		}
		return supplyGeneration;
	}

	private Supply supply;

	private Set<SiteSupplyGeneration> siteSupplyGenerations;

	private HhStartDate startDate;

	private HhStartDate finishDate;
	private MopContract mopContract;
	private String mopAccount;

	private HhdcContract hhdcContract;
	private String hhdcAccount;
	private Pc pc;
	private String meterSerialNumber;
	private Mpan importMpan;

	private Mpan exportMpan;

	private Set<Mpan> mpans;
	private Set<Channel> channels;

	SupplyGeneration() {
	}

	SupplyGeneration(Supply supply, HhStartDate startDate,
			HhStartDate finishDate, MopContract mopContract, String mopAccount,
			HhdcContract hhdcContract, String hhdcAccount,
			String meterSerialNumber) throws HttpException {
		setChannels(new HashSet<Channel>());
		setSupply(supply);
		setSiteSupplyGenerations(new HashSet<SiteSupplyGeneration>());
		setMpans(new HashSet<Mpan>());
		setPc(Pc.getPc("00"));
		setStartDate(startDate);
		setFinishDate(finishDate);
		setMopContract(mopContract);
		setMopAccount(mopAccount);
		setHhdcContract(hhdcContract);
		setHhdcAccount(hhdcAccount);
		setMeterSerialNumber(meterSerialNumber);
	}

	void setSupply(Supply supply) {
		this.supply = supply;
	}

	public Supply getSupply() {
		return supply;
	}

	public Set<SiteSupplyGeneration> getSiteSupplyGenerations() {
		return siteSupplyGenerations;
	}

	protected void setSiteSupplyGenerations(
			Set<SiteSupplyGeneration> siteSupplyGenerations) {
		this.siteSupplyGenerations = siteSupplyGenerations;
	}

	public HhStartDate getStartDate() {
		return startDate;
	}

	void setStartDate(HhStartDate startDate) {
		this.startDate = startDate;
	}

	public HhStartDate getFinishDate() {
		return finishDate;
	}

	void setFinishDate(HhStartDate finishDate) {
		this.finishDate = finishDate;
	}

	public MopContract getMopContract() {
		return mopContract;
	}

	void setMopContract(MopContract mopContract) {
		this.mopContract = mopContract;
	}

	public String getMopAccount() {
		return mopAccount;
	}

	void setMopAccount(String mopAccount) {
		this.mopAccount = mopAccount;
	}

	public HhdcContract getHhdcContract() {
		return hhdcContract;
	}

	void setHhdcContract(HhdcContract hhdcContract) {
		this.hhdcContract = hhdcContract;
	}

	public String getHhdcAccount() {
		return hhdcAccount;
	}

	void setHhdcAccount(String hhdcAccount) {
		this.hhdcAccount = hhdcAccount;
	}

	public Pc getPc() {
		return pc;
	}

	public String getMeterSerialNumber() {
		return meterSerialNumber;
	}

	void setMeterSerialNumber(String meterSerialNumber) {
		this.meterSerialNumber = meterSerialNumber;
	}

	void setPc(Pc pc) {
		this.pc = pc;
	}

	public Mpan getImportMpan() {
		return importMpan;
	}

	void setImportMpan(Mpan importMpan) {
		this.importMpan = importMpan;
	}

	public Mpan getMpan(boolean isImport) {
		return isImport ? getImportMpan() : getExportMpan();
	}

	public Set<Channel> getChannels() {
		return channels;
	}

	void setChannels(Set<Channel> channels) {
		this.channels = channels;
	}

	public Dso getDso() {
		if (importMpan == null) {
			return exportMpan.getCore().getDso();
		} else {
			return importMpan.getCore().getDso();
		}
	}

	public void attachSite(Site site) throws HttpException {
		attachSite(site, false);
	}

	public void attachSite(Site site, boolean isLocation) throws HttpException {
		boolean alreadyThere = false;
		for (SiteSupplyGeneration siteSupplyGeneration : siteSupplyGenerations) {
			if (siteSupplyGeneration.getSite().equals(site)) {
				alreadyThere = true;
				break;
			}
		}
		if (alreadyThere) {
			throw new UserException(
					"The site is already attached to this supply.");
		} else {
			SiteSupplyGeneration siteSupplyGeneration = new SiteSupplyGeneration(
					site, this, false);
			siteSupplyGenerations.add(siteSupplyGeneration);
			site.attachSiteSupplyGeneration(siteSupplyGeneration);
		}
		if (isLocation) {
			setPhysicalLocation(site);
		}
	}

	public void detachSite(Site site) throws HttpException {
		if (siteSupplyGenerations.size() < 2) {
			throw new UserException(
					"A supply has to be attached to at least one site.");
		}
		SiteSupplyGeneration siteSupplyGeneration = (SiteSupplyGeneration) Hiber
				.session()
				.createQuery(
						"from SiteSupplyGeneration siteSupplyGeneration where siteSupplyGeneration.supplyGeneration = :supplyGeneration and siteSupplyGeneration.site = :site")
				.setEntity("supplyGeneration", this).setEntity("site", site)
				.uniqueResult();
		if (siteSupplyGeneration == null) {
			throw new UserException(
					"Can't detach this site, as it wasn't attached in the first place.");
		}
		siteSupplyGenerations.remove(siteSupplyGeneration);
		siteSupplyGenerations.iterator().next().setIsPhysical(true);
		Hiber.flush();
		site.detachSiteSupplyGeneration(siteSupplyGeneration);
		Hiber.flush();
	}

	public Mpan getExportMpan() {
		return exportMpan;
	}

	public void setExportMpan(Mpan exportMpan) {
		this.exportMpan = exportMpan;
	}

	public Set<Mpan> getMpans() {
		return mpans;
	}

	void setMpans(Set<Mpan> mpans) {
		this.mpans = mpans;
	}

	public Channel getChannel(boolean isImport, boolean isKwh) {
		for (Channel candidateChannel : channels) {
			if (candidateChannel.getIsImport() == isImport
					&& candidateChannel.getIsKwh() == isKwh) {
				return candidateChannel;
			}
		}
		return null;
	}

	public void update(HhStartDate startDate, HhStartDate finishDate,
			MopContract mopContract, String mopAccount,
			HhdcContract hhdcContract, String hhdcAccount) throws HttpException {
		String importMpanStr = null;
		Ssc importSsc = null;
		SupplierContract importSupplierContract = null;
		String importSupplierAccount = null;
		Integer importAgreedSupplyCapacity = null;
		String exportMpanStr = null;
		Ssc exportSsc = null;
		SupplierContract exportSupplierContract = null;
		String exportSupplierAccount = null;
		Integer exportAgreedSupplyCapacity = null;
		if (importMpan != null) {
			importMpanStr = importMpan.toString();
			importSsc = importMpan.getSsc();
			importSupplierAccount = importMpan.getSupplierAccount();
			importSupplierContract = importMpan.getSupplierContract();
			importAgreedSupplyCapacity = importMpan.getAgreedSupplyCapacity();
		}
		if (exportMpan != null) {
			exportMpanStr = exportMpan.toString();
			exportSsc = exportMpan.getSsc();
			exportSupplierAccount = exportMpan.getSupplierAccount();
			exportSupplierContract = exportMpan.getSupplierContract();
			exportAgreedSupplyCapacity = exportMpan.getAgreedSupplyCapacity();
		}
		update(startDate, finishDate, mopContract, mopAccount, hhdcContract,
				hhdcAccount, importMpanStr, importSsc, importSupplierContract,
				importSupplierAccount, importAgreedSupplyCapacity,
				exportMpanStr, exportSsc, exportSupplierContract,
				exportSupplierAccount, exportAgreedSupplyCapacity);
	}

	void delete() throws HttpException {
		List<SiteSupplyGeneration> ssGens = new ArrayList<SiteSupplyGeneration>();
		for (SiteSupplyGeneration ssGen : siteSupplyGenerations) {
			ssGens.add(ssGen);
		}
		for (SiteSupplyGeneration ssGen : ssGens) {
			siteSupplyGenerations.remove(ssGen);
			Hiber.flush();
			ssGen.getSite().detachSiteSupplyGeneration(ssGen);
			Hiber.flush();
		}
		List<Channel> ssChannels = new ArrayList<Channel>();
		for (Channel channel : channels) {
			ssChannels.add(channel);
		}
		for (Channel channel : ssChannels) {
			deleteChannel(channel.getIsImport(), channel.getIsKwh());
		}
	}

	public void update(HhStartDate startDate, HhStartDate finishDate)
			throws HttpException {
		if (startDate.equals(this.startDate)
				&& HhStartDate.isEqual(finishDate, this.finishDate)) {
			return;
		} else {
			update(startDate, finishDate, mopContract, mopAccount,
					hhdcContract, hhdcAccount);
		}
	}

	public void update(HhStartDate startDate, HhStartDate finishDate,
			MopContract mopContract, String mopAccount,
			HhdcContract hhdcContract, String hhdcAccount,
			String importMpanStr, Ssc importSsc,
			SupplierContract importSupplierContract,
			String importSupplierAccount, Integer importAgreedSupplyCapacity,
			String exportMpanStr, Ssc exportSsc,
			SupplierContract exportSupplierContract,
			String exportSupplierAccount, Integer exportAgreedSupplyCapacity)
			throws HttpException {
		if (startDate.after(finishDate)) {
			throw new UserException(
					"The generation start date can't be after the finish date.");
		}
		Pc importPc = null;
		Pc exportPc = null;
		if (importMpan == null) {
			if (importMpanStr != null && importMpanStr.length() != 0) {
				Hiber.flush();
				Mpan te = new Mpan(this, importMpanStr, importSsc,
						importSupplierContract, importSupplierAccount,
						importAgreedSupplyCapacity);
				Hiber.session().save(te);
				Hiber.flush();
				setImportMpan(te);
				Hiber.flush();
				mpans.add(getImportMpan());
				Hiber.flush();
			}
		} else {
			if (importMpanStr == null || importMpanStr.length() == 0) {
				mpans.remove(importMpan);
				setImportMpan(null);
			} else {
				importMpan.update(importMpanStr, importSsc,
						importSupplierContract, importSupplierAccount,
						importAgreedSupplyCapacity);
			}
		}
		Hiber.flush();
		if (exportMpan == null) {
			if (exportMpanStr != null && exportMpanStr.length() != 0) {
				setExportMpan(new Mpan(this, exportMpanStr, exportSsc,
						exportSupplierContract, exportSupplierAccount,
						exportAgreedSupplyCapacity));
				mpans.add(getExportMpan());
			}
		} else {
			if (exportMpanStr == null || exportMpanStr.length() == 0) {
				mpans.remove(exportMpan);
				setExportMpan(null);
			} else {
				exportMpan.update(exportMpanStr, exportSsc,
						exportSupplierContract, exportSupplierAccount,
						exportAgreedSupplyCapacity);
			}
		}
		if (importMpan == null && exportMpan == null) {
			throw new UserException(document(),
					"A supply generation must have at least one MPAN.");
		}
		if (importMpan != null) {
			if (!importMpan.getLlfc().getIsImport()) {
				throw new UserException(document(),
						"The import line loss factor '" + importMpan.getLlfc()
								+ "' says that the MPAN is actually export.");
			}
			importPc = Mpan.pc(importMpanStr);
			if (supply.getSource().getCode().equals(Source.NETWORK_CODE)
					&& importMpan.getCore().getDso().getCode().equals("99")) {
				throw new UserException(
						"A network supply can't have a 99 import MPAN.");
			}
		}
		if (exportMpan != null) {
			if (exportMpan.getLlfc().getIsImport()) {
				throw new UserException(
						"Problem with the export MPAN with core '"
								+ exportMpan.getCore()
								+ "'. The Line Loss Factor '"
								+ exportMpan.getLlfc()
								+ "' says that the MPAN is actually import.");
			}
			exportPc = Mpan.pc(exportMpanStr);
		}
		if (importMpan != null && exportMpan != null) {
			if (!importMpan.getCore().getDso().equals(
					exportMpan.getCore().getDso())) {
				throw new UserException(
						"Two MPANs on the same supply generation must have the same DSO.");
			}
			if (!importMpan.getLlfc().getVoltageLevel().equals(
					exportMpan.getLlfc().getVoltageLevel())) {
				throw new UserException(
						"The voltage level indicated by the Line Loss Factor must be the same for both the MPANs.");
			}
			if (!importPc.equals(exportPc)) {
				throw new UserException(
						"The Profile Classes of both MPANs must be the same.");
			}
		}
		Dso dso = getDso();
		if (dso.getCode().equals("22")) {
			/*
			 * if (importMpan != null) { LineLossFactorCode code =
			 * importLineLossFactor.getCode(); if ((code.equals(new
			 * LineLossFactorCode("520")) || code.equals(new
			 * LineLossFactorCode("550")) || code .equals(new
			 * LineLossFactorCode("580"))) && getExportMpan() == null) { throw
			 * UserException .newOk("The Line Loss Factor of the import MPAN
			 * says that there should be an export MPAN, but there isn't one.");
			 * } }
			 */

			if (getExportMpan() != null && getImportMpan() != null) {
				int code = getImportMpan().getLlfc().getCode();
				if (code != 520 && code != 550 && code != 580) {
					throw new UserException(
							"The DSO is 22, there's an export MPAN and the Line Loss Factor of the import MPAN "
									+ getImportMpan()
									+ " can only be 520, 550 or 580.");
				}
			}
		}
		if (importPc == null) {
			setPc(exportPc);
		} else {
			setPc(importPc);
		}
		SupplyGeneration previousGeneration = (SupplyGeneration) Hiber
				.session()
				.createQuery(
						"from SupplyGeneration generation where generation.supply = :supply and generation.startDate.date < :startDate order by generation.startDate.date")
				.setEntity("supply", supply).setTimestamp("startDate",
						startDate.getDate()).setMaxResults(1).uniqueResult();
		if (previousGeneration == null) {
			if (((Long) Hiber
					.session()
					.createQuery(
							"select count(*) from HhDatum datum where datum.channel.supplyGeneration.supply  = :supply and datum.startDate.date < :date")
					.setEntity("supply", supply).setTimestamp("date",
							startDate.getDate()).uniqueResult()) > 0) {
				throw new UserException(
						"There are HH data before the start of the updated supply.");
			}
			if (((Long) Hiber
					.session()
					.createQuery(
							"select count(*) from RegisterRead read where read.bill.supply  = :supply and read.presentDate.date < :date")
					.setEntity("supply", supply).setTimestamp("date",
							startDate.getDate()).uniqueResult()) > 0) {
				throw new UserException(
						"There are register reads before the start of the updated supply.");
			}
			if (((Long) Hiber
					.session()
					.createQuery(
							"select count(*) from Bill bill where bill.supply  = :supply and bill.startDate.date < :date")
					.setEntity("supply", supply).setTimestamp("date",
							startDate.getDate()).uniqueResult()) > 0) {
				throw new UserException(
						"There are bills before the start of the updated supply.");
			}

		} else {
			boolean isOverlap = false;
			if (importMpan != null) {
				Mpan prevImportMpan = previousGeneration.getImportMpan();
				if (prevImportMpan != null
						&& importMpan.getCore()
								.equals(prevImportMpan.getCore())) {
					isOverlap = true;
				}
			}
			if (!isOverlap && exportMpan != null) {
				Mpan prevExportMpan = previousGeneration.getExportMpan();
				if (prevExportMpan != null
						&& exportMpan.getCore()
								.equals(prevExportMpan.getCore())) {
					isOverlap = true;
				}
			}
			if (!isOverlap) {
				throw new UserException(
						"MPAN cores can't change without an overlapping period.");
			}
		}
		setStartDate(startDate);
		setFinishDate(finishDate);
		SupplyGeneration nextGeneration = supply.getGenerationNext(this);
		if (nextGeneration == null) {
			if (finishDate != null
					&& ((Long) Hiber
							.session()
							.createQuery(
									"select count(*) from HhDatum datum where datum.channel.supplyGeneration.supply  = :supply and datum.startDate.date > :date")
							.setEntity("supply", supply).setTimestamp("date",
									finishDate.getDate()).uniqueResult()) > 0) {
				throw new UserException("There are HH data after " + finishDate
						+ ", the end of the updated supply.");
			}
			if (finishDate != null
					&& ((Long) Hiber
							.session()
							.createQuery(
									"select count(*) from Bill bill where bill.supply  = :supply and bill.startDate.date > :date")
							.setEntity("supply", supply).setTimestamp("date",
									finishDate.getDate()).uniqueResult()) > 0) {
				throw new UserException(
						"There are bills after the end of the updated supply.");
			}
		} else {
			boolean isOverlap = false;
			if (importMpan != null) {
				Mpan nextImportMpan = nextGeneration.getImportMpan();
				if (nextImportMpan != null
						&& importMpan.getCore()
								.equals(nextImportMpan.getCore())) {
					isOverlap = true;
				}
			}
			if (!isOverlap && exportMpan != null) {
				Mpan nextExportMpan = nextGeneration.getExportMpan();
				if (nextExportMpan != null
						&& exportMpan.getCore()
								.equals(nextExportMpan.getCore())) {
					isOverlap = true;
				}
			}
			if (!isOverlap) {
				throw new UserException(
						"MPAN cores can't change without an overlapping period.");
			}
		}
		if (hhdcContract == null) {
			hhdcAccount = null;
			if (!channels.isEmpty()) {
				throw new UserException(
						"Can't remove the HHDC account while there are still channels there.");
			}
		} else {
			hhdcAccount = hhdcAccount == null ? null : hhdcAccount.trim();
			if (hhdcAccount == null || hhdcAccount.length() == 0) {
				throw new UserException(
						"If there's a HHDC contract, there must be an account reference.");
			}
			HhStartDate hhdcContractStartDate = hhdcContract
					.getStartRateScript().getStartDate();
			if (hhdcContractStartDate.after(startDate)) {
				throw new UserException(
						"The HHDC contract starts after the supply generation.");
			}
			HhStartDate hhdcContractFinishDate = hhdcContract
					.getFinishRateScript().getFinishDate();
			if (HhStartDate.isBefore(hhdcContractFinishDate, finishDate)) {
				throw new UserException("The HHDC contract "
						+ hhdcContract.getId()
						+ " finishes before the supply generation.");
			}
			Criteria crit = Hiber.session().createCriteria(Bill.class).add(
					Restrictions.eq("supply", supply)).add(
					Restrictions.ge("finishDate.date", startDate.getDate()))
					.createAlias("batch", "bt").add(
							Restrictions.eq("bt.contract.id", hhdcContract
									.getId())).addOrder(
							Order.asc("startDate.date"));
			if (finishDate != null) {
				crit.add(Restrictions
						.le("startDate.date", finishDate.getDate()));
			}
			Bill firstHhdcBill = (Bill) crit.uniqueResult();
			if (firstHhdcBill == null) {
				supply.addSnag(hhdcContract, SupplySnag.MISSING_BILL,
						getStartDate(), getFinishDate());
			} else {
				if (firstHhdcBill.getStartDate().after(getStartDate())) {
					supply.addSnag(hhdcContract, SupplySnag.MISSING_BILL,
							getStartDate(), firstHhdcBill.getStartDate()
									.getPrevious());
				}
			}
		}
		Hiber.flush();
		setHhdcAccount(hhdcAccount);
		setHhdcContract(hhdcContract);
		Hiber.flush();
		for (Channel channel : channels) {
			channel.onSupplyGenerationChange();
		}
		Hiber.flush();
		if (importMpan != null) {
			Criteria impCrit = Hiber.session().createCriteria(Bill.class).add(
					Restrictions.eq("supply", supply)).add(
					Restrictions
							.ge("finishDate.date", getStartDate().getDate()))
					.createAlias("batch", "bt").add(
							Restrictions.eq("bt.contract.id", importMpan
									.getSupplierContract().getId())).addOrder(
							Order.asc("startDate.date")).setMaxResults(1);
			if (finishDate != null) {
				impCrit.add(Restrictions.le("startDate.date", finishDate
						.getDate()));
			}
			Bill firstImpSupBill = (Bill) impCrit.uniqueResult();
			if (firstImpSupBill == null) {
				supply.addSnag(importMpan.getSupplierContract(),
						SupplySnag.MISSING_BILL, getStartDate(),
						getFinishDate());
			} else {
				if (firstImpSupBill.getStartDate().after(getStartDate())) {
					supply.addSnag(importMpan.getSupplierContract(),
							SupplySnag.MISSING_BILL, getStartDate(),
							firstImpSupBill.getStartDate().getPrevious());
				}
			}
		}
		if (exportMpan != null) {
			Criteria expCrit = Hiber.session().createCriteria(Bill.class).add(
					Restrictions.eq("supply", supply)).createAlias("batch",
					"bt").add(
					Restrictions.eq("bt.contract.id", exportMpan
							.getSupplierContract().getId())).add(
					Restrictions
							.ge("finishDate.date", getStartDate().getDate()))
					.addOrder(Order.asc("startDate.date"));
			if (finishDate != null) {
				expCrit.add(Restrictions.le("startDate.date", finishDate
						.getDate()));
			}
			Bill firstExpSupBill = (Bill) expCrit.uniqueResult();
			if (firstExpSupBill == null) {
				supply.addSnag(exportMpan.getSupplierContract(),
						SupplySnag.MISSING_BILL, getStartDate(),
						getFinishDate());
			} else {
				if (firstExpSupBill.getStartDate().after(getStartDate())) {
					supply.addSnag(exportMpan.getSupplierContract(),
							SupplySnag.MISSING_BILL, getStartDate(),
							firstExpSupBill.getStartDate().getPrevious());
				}
			}
		}
		Debug.print("About to move hh data from one generation to another.");
		// See if we have to move hh data from one generation to the other
		for (Boolean isImport : new Boolean[] { true, false }) {
			for (Boolean isKwh : new Boolean[] { true, false }) {
				Channel targetChannel = getChannel(isImport, isKwh);
				Query query = Hiber
						.session()
						.createQuery(
								"select datum.startDate, datum.value, datum.status from HhDatum datum where datum.channel.supplyGeneration.supply = :supply and datum.channel.isImport = :isImport and datum.channel.isKwh = :isKwh and datum.startDate.date >= :from"
										+ (finishDate == null ? ""
												: " and datum.startDate.date <= :to")
										+ (targetChannel == null ? ""
												: " and datum.channel != :targetChannel"))
						.setEntity("supply", supply).setBoolean("isImport",
								isImport).setBoolean("isKwh", isKwh)
						.setTimestamp("from", startDate.getDate());
				if (finishDate != null) {
					query.setTimestamp("to", finishDate.getDate());
				}
				if (targetChannel != null) {
					query.setEntity("targetChannel", targetChannel);
				}
				ScrollableResults hhData = query.scroll();
				HhStartDate groupStart = null;
				if (hhData.next()) {
					groupStart = (HhStartDate) hhData.get(0);
					if (targetChannel == null) {
						throw new UserException(
								"There is no channel for the HH datum starting: "
										+ groupStart.toString()
										+ " is import? "
										+ isImport
										+ " is kWh? "
										+ isKwh
										+ " to move to in the generation starting "
										+ startDate + ", finishing "
										+ finishDate + ".");
					}
					Query channelUpdate = Hiber
							.session()
							.createSQLQuery(
									"update hh_datum set channel_id = :targetChannelId from channel, supply_generation where hh_datum.start_date >= :startDate and channel.id = hh_datum.channel_id and supply_generation.id = channel.supply_generation_id and channel.is_import = :isImport and channel.is_kwh = :isKwh and supply_generation.supply_id = :supplyId"
											+ (finishDate == null ? ""
													: " and hh_datum.start_date <= :finishDate"))
							.setLong("supplyId", supply.getId()).setBoolean(
									"isImport", isImport).setBoolean("isKwh",
									isKwh).setLong("targetChannelId",
									targetChannel.getId()).setTimestamp(
									"startDate", startDate.getDate());
					if (finishDate != null) {
						channelUpdate.setTimestamp("finishDate", finishDate
								.getDate());
					}
					channelUpdate.executeUpdate();
					HhStartDate groupFinish = groupStart;

					hhData.beforeFirst();
					while (hhData.next()) {
						HhStartDate hhStartDate = (HhStartDate) hhData.get(0);
						if (groupFinish.getNext().before(hhStartDate)) {
							targetChannel.deleteSnag(ChannelSnag.SNAG_MISSING,
									groupStart, groupFinish);
							groupStart = groupFinish = hhStartDate;
						} else {
							groupFinish = hhStartDate;
						}
						if (((BigDecimal) hhData.get(1)).doubleValue() < 0) {
							targetChannel.addSnag(ChannelSnag.SNAG_NEGATIVE,
									hhStartDate, hhStartDate);
						}
						if ((Character) hhData.get(2) != HhDatum.ACTUAL) {
							targetChannel.addSnag(ChannelSnag.SNAG_ESTIMATED,
									hhStartDate, hhStartDate);
						}
					}
					targetChannel.deleteSnag(ChannelSnag.SNAG_MISSING,
							groupStart, groupFinish);
					hhData.close();
				}
			}
		}
		Debug.print("finishing moving hh data");
		Hiber.flush();
		for (Mpan mpan : mpans) {
			SupplierContract supplierContract = mpan.getSupplierContract();
			if (supplierContract.getStartRateScript().getStartDate().after(
					startDate)) {
				throw new UserException(
						"The supplier contract starts after the supply generation.");
			}
			if (HhStartDate.isBefore(supplierContract.getFinishRateScript()
					.getFinishDate(), finishDate)) {
				throw new UserException(
						"The supplier contract finishes before the supply generation.");
			}
		}
		Hiber.flush();
	}

	public int compareTo(Object obj) {
		return getFinishDate().getDate().compareTo(
				((SupplyGeneration) obj).getFinishDate().getDate());
	}

	public void deleteMpan(Mpan mpan) throws HttpException {
		if (mpans.size() < 2) {
			throw new UserException(
					"There must be at least one MPAN generation in each supply generation.");
		}
		mpans.remove(mpan);
	}

	public Element toXml(Document doc) throws HttpException {
		Element element = super.toXml(doc, "supply-generation");
		startDate.setLabel("start");
		element.appendChild(startDate.toXml(doc));
		if (finishDate != null) {
			finishDate.setLabel("finish");
			element.appendChild(finishDate.toXml(doc));
		}
		if (hhdcAccount != null) {
			element.setAttribute("hhdc-account", hhdcAccount);
		}
		return element;
	}

	public Channel insertChannel(boolean isImport, boolean isKwh)
			throws HttpException {
		if (hhdcAccount == null) {
			throw new UserException(
					"Can't add a channel if there's no HHDC account.");
		}
		Channel channel = new Channel(this, isImport, isKwh);
		try {
			Hiber.session().save(channel);
			Hiber.flush();
		} catch (ConstraintViolationException e) {
			throw new UserException("There's already a channel with import: "
					+ isImport + " and kWh: " + isKwh + ".");
		}
		channels.add(channel);
		channel.addSnag(ChannelSnag.SNAG_MISSING, getStartDate(),
				getFinishDate());
		return channel;
	}

	public void httpGet(Invocation inv) throws HttpException {
		inv.sendOk(document());
	}

	@SuppressWarnings("unchecked")
	private Document document() throws HttpException {
		Document doc = MonadUtils.newSourceDocument();
		Element source = doc.getDocumentElement();
		Element generationElement = (Element) toXml(doc, new XmlTree(
				"siteSupplyGenerations", new XmlTree("site")).put("pc").put(
				"supply", new XmlTree("source").put("gspGroup")).put(
				"mopContract", new XmlTree("party")).put("hhdcContract",
				new XmlTree("party")));
		source.appendChild(generationElement);
		for (Mpan mpan : mpans) {
			Element mpanElement = (Element) mpan.toXml(doc, new XmlTree("core")
					.put("mtc").put("llfc").put("ssc").put("supplierContract",
							new XmlTree("party")));
			generationElement.appendChild(mpanElement);
			/*
			 * for (RegisterRead read : (List<RegisterRead>) Hiber.session()
			 * .createQuery( "from RegisterRead read where read.mpan = :mpan")
			 * .setEntity("mpan", mpan).list()) {
			 * mpanElement.appendChild(read.toXml(doc, new XmlTree("invoice",
			 * new XmlTree("batch", new XmlTree("contract", new
			 * XmlTree("party")))))); }
			 */
		}
		source.appendChild(MonadDate.getMonthsXml(doc));
		source.appendChild(MonadDate.getDaysXml(doc));
		source.appendChild(new MonadDate().toXml(doc));
		for (Pc pc : (List<Pc>) Hiber.session().createQuery(
				"from Pc pc order by pc.code").list()) {
			source.appendChild(pc.toXml(doc));
		}
		for (GspGroup group : (List<GspGroup>) Hiber.session().createQuery(
				"from GspGroup group order by group.code").list()) {
			source.appendChild(group.toXml(doc));
		}
		for (MopContract contract : (List<MopContract>) Hiber
				.session()
				.createQuery("from MopContract contract order by contract.name")
				.list()) {
			source.appendChild(contract.toXml(doc));
		}
		for (HhdcContract contract : (List<HhdcContract>) Hiber.session()
				.createQuery(
						"from HhdcContract contract order by contract.name")
				.list()) {
			source.appendChild(contract.toXml(doc));
		}
		for (SupplierContract contract : (List<SupplierContract>) Hiber
				.session()
				.createQuery(
						"from SupplierContract contract order by contract.name")
				.list()) {
			source.appendChild(contract.toXml(doc));
		}
		return doc;
	}

	public void httpPost(Invocation inv) throws HttpException {
		Document doc = document();
		try {
			if (inv.hasParameter("delete")) {
				supply.deleteGeneration(this);
				Hiber.commit();
				inv.sendSeeOther(new SupplyGenerations(supply).getUri());
			} else if (inv.hasParameter("attach")) {
				String siteCode = inv.getString("site-code");
				if (!inv.isValid()) {
					throw new UserException(document());
				}
				Site site = Site.getSite(siteCode);
				attachSite(site);
				Hiber.commit();
				inv.sendOk(document());
			} else if (inv.hasParameter("detach")) {
				Long siteId = inv.getLong("site-id");
				if (!inv.isValid()) {
					throw new UserException(document());
				}
				Site site = Site.getSite(siteId);
				detachSite(site);
				Hiber.commit();
				inv.sendOk(document());
			} else if (inv.hasParameter("set-location")) {
				Long siteId = inv.getLong("site-id");
				if (!inv.isValid()) {
					throw new UserException(document());
				}
				Site site = Site.getSite(siteId);
				setPhysicalLocation(site);
				Hiber.commit();
				inv.sendOk(document());
			} else {
				Date startDate = inv.getDate("start");
				Long mopContractId = inv.getLong("mop-contract-id");
				Long hhdcContractId = inv.getLong("hhdc-contract-id");
				Long pcId = inv.getLong("pc-id");
				if (!inv.isValid()) {
					throw new UserException();
				}
				HhStartDate finishDate = null;
				String importMpanStr = null;
				Ssc importSsc = null;
				Integer importAgreedSupplyCapacity = null;
				MopContract mopContract = null;
				HhdcContract hhdcContract = null;
				SupplierContract importSupplierContract = null;
				String importSupplierAccount = null;
				boolean isEnded = inv.getBoolean("is-ended");
				if (isEnded) {
					Date finishDateRaw = inv.getDate("finish");
					Calendar cal = MonadDate.getCalendar();
					cal.setTime(finishDateRaw);
					cal.add(Calendar.DAY_OF_MONTH, 1);
					finishDate = new HhStartDate(cal.getTime()).getPrevious();
				}
				String mopAccount = null;
				if (mopContractId != null) {
					mopContract = MopContract.getMopContract(mopContractId);
					mopAccount = inv.getString("mop-account");
				}
				String hhdcAccount = null;
				if (hhdcContractId != null) {
					hhdcContract = HhdcContract.getHhdcContract(hhdcContractId);
					hhdcAccount = inv.getString("hhdc-account");
				}
				Pc pc = Pc.getPc(pcId);
				boolean hasImportMpan = inv.getBoolean("has-import-mpan");

				if (hasImportMpan) {
					String importMpanCoreStr = inv
							.getString("import-mpan-core");
					String importLlfcCodeStr = inv
							.getString("import-llfc-code");
					String importMtcCode = inv.getString("import-mtc-code");
					String importSscCode = inv.getString("import-ssc-code");
					Long importSupplierContractId = inv
							.getLong("import-supplier-contract-id");
					importSupplierAccount = inv
							.getString("import-supplier-account");

					if (!inv.isValid()) {
						throw new UserException(document());
					}
					if (importSscCode.trim().length() > 0) {
						importSsc = Ssc.getSsc(importSscCode);
					}

					importMpanStr = pc.codeAsString() + importMtcCode
							+ importLlfcCodeStr + importMpanCoreStr;
					importAgreedSupplyCapacity = inv
							.getInteger("import-agreed-supply-capacity");

					if (!inv.isValid()) {
						throw new UserException();
					}
					importSupplierContract = SupplierContract
							.getSupplierContract(importSupplierContractId);
				}
				String exportMpanStr = null;
				Ssc exportSsc = null;
				Integer exportAgreedSupplyCapacity = null;
				String exportSupplierAccount = null;
				SupplierContract exportSupplierContract = null;
				boolean hasExportMpan = inv.getBoolean("has-export-mpan");
				if (hasExportMpan) {
					String exportMpanCoreStr = inv
							.getString("export-mpan-core");
					String llfcCodeStr = inv.getString("export-llfc-code");
					String exportMtcCode = inv.getString("export-mtc-code");
					String exportSscCode = inv.getString("export-ssc-code");
					if (!inv.isValid()) {
						throw new UserException();
					}
					if (exportSscCode.trim().length() > 0) {
						exportSsc = Ssc.getSsc(exportSscCode);
					}
					exportMpanStr = pc.codeAsString() + exportMtcCode
							+ llfcCodeStr + exportMpanCoreStr;
					exportAgreedSupplyCapacity = inv
							.getInteger("export-agreed-supply-capacity");
					if (!inv.isValid()) {
						throw new UserException();
					}
					Long exportSupplierContractId = inv
							.getLong("export-supplier-contract-id");
					exportSupplierContract = SupplierContract
							.getSupplierContract(exportSupplierContractId);
					exportSupplierAccount = inv
							.getString("export-supplier-account");
				}
				supply.updateGeneration(this, new HhStartDate(startDate),
						finishDate);
				Hiber.flush();
				update(getStartDate(), getFinishDate(), mopContract,
						mopAccount, hhdcContract, hhdcAccount, importMpanStr,
						importSsc, importSupplierContract,
						importSupplierAccount, importAgreedSupplyCapacity,
						exportMpanStr, exportSsc, exportSupplierContract,
						exportSupplierAccount, exportAgreedSupplyCapacity);
				Hiber.commit();
				inv.sendOk(document());
			}
		} catch (HttpException e) {
			e.setDocument(doc);
			throw e;
		}
	}

	public MonadUri getUri() throws HttpException {
		return supply.getSupplyGenerationsInstance().getUri().resolve(
				getUriId()).append("/");
	}

	public Urlable getChild(UriPathElement uriId) throws HttpException {
		if (Channels.URI_ID.equals(uriId)) {
			return new Channels(this);
		} else {
			throw new NotFoundException();
		}
	}

	public String toString() {
		return "SupplyGeneration id " + getId();
	}

	public void setPhysicalLocation(Site site) throws HttpException {
		SiteSupplyGeneration targetSiteSupply = null;
		for (SiteSupplyGeneration siteSupply : siteSupplyGenerations) {
			if (site.equals(siteSupply.getSite())) {
				targetSiteSupply = siteSupply;
			}
		}
		if (targetSiteSupply == null) {
			throw new UserException("The site isn't attached to this supply.");
		}
		for (SiteSupplyGeneration siteSupply : siteSupplyGenerations) {
			siteSupply.setIsPhysical(siteSupply.equals(targetSiteSupply));
		}
		Hiber.flush();
	}

	public Channels getChannelsInstance() {
		return new Channels(this);
	}

	@SuppressWarnings("unchecked")
	public void deleteChannel(boolean isImport, boolean isKwh)
			throws HttpException {
		Channel channel = getChannel(isImport, isKwh);
		if ((Long) Hiber
				.session()
				.createQuery(
						"select count(*) from HhDatum datum where datum.channel = :channel")
				.setEntity("channel", channel).uniqueResult() > 0) {
			throw new UserException(
					"One can't delete a channel if there are still HH data attached to it.");
		}
		// delete any concommitant snags
		for (ChannelSnag snag : (List<ChannelSnag>) Hiber.session()
				.createQuery(
						"from ChannelSnag snag where snag.channel = :channel")
				.setEntity("channel", channel).list()) {
			ChannelSnag.deleteChannelSnag(snag);
		}
		channels.remove(channel);
		Hiber.session().flush();
	}
}
