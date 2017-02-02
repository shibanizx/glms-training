package ext.jci;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;

import com.ptc.core.lwc.server.PersistableAdapter;
import com.ptc.core.meta.common.Hyperlink;
import com.ptc.core.meta.common.UpdateOperationIdentifier;
import com.ptc.netmarkets.model.NmOid;
import com.ptc.netmarkets.util.beans.NmURLFactoryBean;
import com.ptc.netmarkets.util.misc.NetmarketURL;

import wt.content.ApplicationData;
import wt.content.ContentRoleType;
import wt.content.ContentServerHelper;
import wt.doc.DocumentType;
import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.folder.FolderHelper;
import wt.iba.value.URLValue;
import wt.inf.container.WTContainer;
import wt.inf.container.WTContainerRef;
import wt.method.RemoteAccess;
import wt.org.WTPrincipalReference;
import wt.part.WTPart;
import wt.part.WTPartHelper;
import wt.part.WTPartMaster;
import wt.part.WTPartUsageLink;
import wt.pdmlink.PDMLinkProduct;
import wt.pom.Transaction;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.session.SessionHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.VersionControlException;
import wt.vc.VersionControlHelper;
import wt.vc.VersionControlServerHelper;
import wt.vc.config.LatestConfigSpec;
import wt.vc.wip.WorkInProgressHelper;

public class Training implements RemoteAccess {

	public static void fileUpload() throws WTException {

		String localAddress = "C:\\Users\\css7\\Desktop\\Example";
		File file = new File(localAddress);
		File[] list = file.listFiles();

		WTContainer container = getContainer(PDMLinkProduct.class, PDMLinkProduct.NAME, "GOLF_CART");

		for (int i = 0; i < list.length; i++) {

			if(list[i].isFile()) {

				String fileNameWithExtension = list[i].getName();
				String label = FilenameUtils.removeExtension(fileNameWithExtension);

				Transaction trnx = new Transaction();
				trnx.start();

				System.out.println("Starting transaction");

				try {

					FileInputStream input = new FileInputStream(localAddress + "\\" + fileNameWithExtension);

					WTDocument doc = null;

					String link = null;

					QueryResult usageLinks = getWTPartUsageLinks(label);

					if(!usageLinks.hasMoreElements()) {

						System.out.println("Product Label not found");

					}
					else
					{

						System.out.println("Label found " + label);

						doc = WTDocument.newWTDocument(label, label, DocumentType.getDocumentTypeDefault());

						if(container != null){
							FolderHelper.assignLocation(doc, "/Default/Example", WTContainerRef.newWTContainerRef(container));
							doc = (WTDocument) PersistenceHelper.manager.save(doc);	
							doc = setContent(doc, fileNameWithExtension, label, input);
							link = getDetailsURL(doc);
						}

					}

					while (usageLinks.hasMoreElements()) {

						Persistable[] persistable = (Persistable[]) usageLinks.nextElement();

						WTPartUsageLink usageLink = (WTPartUsageLink) persistable[1];					

						setLabelControlParameter(usageLink, label, link);
					}

					System.out.println("Committing transaction");
					
					trnx.commit();
					trnx = null;

				}
				catch(Exception e) {
					e.printStackTrace();
					trnx.rollback();
				}

			}

		}

	}

	public static WTDocument setContent(WTDocument doc, String fileNameWithExt, String fileNameWithoutExt, FileInputStream input) throws WTException, FileNotFoundException, PropertyVetoException, IOException{

		ApplicationData content = ApplicationData.newApplicationData(doc);

		content.setFileName(fileNameWithoutExt);
		content.setUploadedFromPath(fileNameWithExt);
		content.setRole(ContentRoleType.PRIMARY);
		content.setFileSize(fileNameWithoutExt.length());
		content.setCreatedBy(WTPrincipalReference.newWTPrincipalReference(SessionHelper.getPrincipal()));

		System.out.println("Checkout the WTDocument");

		doc = (WTDocument) WorkInProgressHelper.service.checkout(doc, WorkInProgressHelper.service.getCheckoutFolder(), "").getWorkingCopy();

		System.out.println("Setting content to : " + doc.getName());

		content = ContentServerHelper.service.updateContent(doc, content, input);	

		doc = (WTDocument) ContentServerHelper.service.updateHolderFormat(doc);

		System.out.println("Checkin WTDocument");

		doc = (WTDocument) WorkInProgressHelper.service.checkin(doc, "success doc");

		return doc;
	}

	@SuppressWarnings("deprecation")
	public static void setLabelControlParameter(WTPartUsageLink usageLink, String label, String link) throws VersionControlException, WTException {

		WTPart parent = usageLink.getUsedBy();

		parent = (WTPart) VersionControlHelper.service.getLatestIteration(parent, true);

		WTPartMaster child = usageLink.getUses();

		try {

			if (!WorkInProgressHelper.isCheckedOut(parent)) {

				System.out.println("Checking out WTPart:  " + parent);
				
				parent = (WTPart) WorkInProgressHelper.service.checkout(parent, WorkInProgressHelper.service.getCheckoutFolder(), "checked out").getWorkingCopy();

			}

			QueryResult result = WTPartHelper.service.getUsesWTParts(parent, new LatestConfigSpec());

			while(result.hasMoreElements()) {

				Persistable[] persistable = (Persistable[]) result.nextElement();

				WTPartUsageLink tempLink = (WTPartUsageLink) persistable[0];

				WTPartMaster tempChild = tempLink.getUses();

				if(tempChild.getNumber().equalsIgnoreCase(child.getNumber()))
				{
					
					PersistableAdapter obj = new PersistableAdapter(tempLink, null, Locale.US, new UpdateOperationIdentifier());

					obj.load("LabelControl");

					Hyperlink URL = new Hyperlink(link, label);

					obj.set("LabelControl", URL);

					tempLink = (WTPartUsageLink) obj.apply();

					tempLink = (WTPartUsageLink) PersistenceHelper.manager.modify(tempLink);

					break;
				}
				
			}
			
			if (WorkInProgressHelper.isCheckedOut(parent)) {

				parent = (WTPart) WorkInProgressHelper.service.checkin(parent, "");

				System.out.println("Checkin WTPart: " + parent.getName());

			}

		} catch (WTPropertyVetoException | WTException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	public static String getDetailsURL(WTDocument doc) throws VersionControlException, WTException {

		doc = (WTDocument) VersionControlHelper.service.getLatestIteration(doc, true);
		NmOid oid = new NmOid("WTDocument", doc.getPersistInfo().getObjectIdentifier());
		NmURLFactoryBean bean = new NmURLFactoryBean();

		String URL = NetmarketURL.buildURL(bean, "object", "view", oid);

		return URL;
	}

	public static QueryResult getWTPartUsageLinks(String label) throws WTException {


		QuerySpec querySpec = new QuerySpec();

		int partIndex = querySpec.appendClassList(WTPart.class, true);

		int linkIndex = querySpec.appendClassList(WTPartUsageLink.class, true);

		int attributeIndex = querySpec.appendClassList(URLValue.class, true);

		querySpec.appendJoin(linkIndex, WTPartUsageLink.USED_BY_ROLE, partIndex);

		querySpec.appendWhere(new SearchCondition(WTPartUsageLink.class, "thePersistInfo.theObjectIdentifier.id", URLValue.class, "theIBAHolderReference.key.id"), new int[] {linkIndex, attributeIndex});

		querySpec.appendAnd();

		querySpec.appendWhere(new SearchCondition(URLValue.class, URLValue.DESCRIPTION, SearchCondition.EQUAL, label), new int[]{attributeIndex});

		querySpec.appendAnd();

		querySpec.appendWhere(new SearchCondition(WTPart.class, WTPart.LATEST_ITERATION, "TRUE"), new int[]{partIndex});

		QueryResult usageLinks = PersistenceHelper.manager.find(querySpec);

		return usageLinks;
	}

	public static WTContainer getContainer(Class containerType, String attribute, String containerName) throws WTException{

		QuerySpec qs = new QuerySpec(PDMLinkProduct.class);
		SearchCondition sc = new SearchCondition(containerType, attribute, SearchCondition.LIKE, containerName, true);
		qs.appendSearchCondition(sc);

		QueryResult qr = PersistenceHelper.manager.find(qs);
		WTContainer container = null;

		if(qr.hasMoreElements()){
			container = (WTContainer) qr.nextElement();
		}

		return container;
	}


}
