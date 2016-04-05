/**
 * OpenCPS is the open source Core Public Services software Copyright (C)
 * 2016-present OpenCPS community This program is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3
 * of the License, or any later version. This program is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details. You should have received a
 * copy of the GNU Affero General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>
 */

package org.opencps.accountmgt.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencps.accountmgt.NoSuchCitizenException;
import org.opencps.accountmgt.model.Citizen;
import org.opencps.accountmgt.service.base.CitizenLocalServiceBaseImpl;
import org.opencps.util.DLFolderUtil;
import org.opencps.util.DateTimeUtil;
import org.opencps.util.PortletConstants;
import org.opencps.util.PortletPropsValues;
import org.opencps.util.PortletUtil;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.model.Address;
import com.liferay.portal.model.EmailAddress;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.Phone;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.Ticket;
import com.liferay.portal.model.TicketConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.model.UserGroup;
import com.liferay.portal.model.Website;
import com.liferay.portal.security.auth.PrincipalThreadLocal;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.TicketLocalServiceUtil;
import com.liferay.portal.service.UserGroupLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.SubscriptionSender;
import com.liferay.portlet.announcements.model.AnnouncementsDelivery;
import com.liferay.portlet.documentlibrary.model.DLFolder;
import com.liferay.portlet.documentlibrary.service.DLAppServiceUtil;
import com.liferay.portlet.documentlibrary.service.DLFolderLocalServiceUtil;
import com.liferay.util.PwdGenerator;

/**
 * The implementation of the citizen local service. <p> All custom service
 * methods should be put in this class. Whenever methods are added, rerun
 * ServiceBuilder to copy their definitions into the
 * {@link org.opencps.accountmgt.service.CitizenLocalService} interface. <p>
 * This is a local service. Methods of this service will not have security
 * checks based on the propagated JAAS credentials because this service can only
 * be accessed from within the same VM. </p>
 *
 * @author khoavd
 * @author trungnt
 * @see org.opencps.accountmgt.service.base.CitizenLocalServiceBaseImpl
 * @see org.opencps.accountmgt.service.CitizenLocalServiceUtil
 */
public class CitizenLocalServiceImpl extends CitizenLocalServiceBaseImpl {
	/*
	 * NOTE FOR DEVELOPERS: Never reference this interface directly. Always use
	 * {@link org.opencps.accountmgt.service.CitizenLocalServiceUtil} to access
	 * the citizen local service.
	 */

	public Citizen addCitizen(
	    String fullName, String personalId, int gender, int birthDateDay,
	    int birthDateMonth, int birthDateYear, String address, String cityCode,
	    String districtCode, String wardCode, String cityName,
	    String districtName, String wardName, String email, String telNo,
	    long repositoryId, String sourceFileName, String mimeType, String title,
	    InputStream inputStream, long size, ServiceContext serviceContext)
	    throws SystemException, PortalException {

		long citizenId = CounterLocalServiceUtil
		    .increment(Citizen.class
		        .getName());

		Citizen citizen = citizenPersistence
		    .create(citizenId);

		Date now = new Date();

		Date birthDate = DateTimeUtil
		    .getDate(birthDateDay, birthDateMonth, birthDateYear);

		PortletUtil.SplitName spn = PortletUtil
		    .splitName(fullName);

		boolean autoPassword = true;
		boolean autoScreenName = true;
		boolean sendEmail = false;

		long[] groupIds = null;
		long[] organizationIds = null;
		long[] roleIds = null;
		long[] userGroupIds = null;

		String password1 = null;
		String password2 = null;
		String screenName = null;

		UserGroup userGroup = null;
		try {
			userGroup = UserGroupLocalServiceUtil
			    .getUserGroup(serviceContext
			        .getCompanyId(),
			        PortletPropsValues.USERMGT_USERGROUP_NAME_CITIZEN);
		}
		catch (Exception e) {
			_log
			    .warn(e
			        .getMessage());
		}
		if (userGroup == null) {
			userGroup = UserGroupLocalServiceUtil
			    .addUserGroup(serviceContext
			        .getUserId(), serviceContext
			            .getCompanyId(),
			        PortletPropsValues.USERMGT_USERGROUP_NAME_CITIZEN,
			        StringPool.BLANK, serviceContext);

			userGroupIds = new long[] {
			    userGroup
			        .getUserGroupId()
			};
		}

		try {
			userGroup = UserGroupLocalServiceUtil
			    .getUserGroup(serviceContext
			        .getCompanyId(),
			        PortletPropsValues.USERMGT_USERGROUP_NAME_CITIZEN);
		}
		catch (Exception e) {
			_log
			    .warn(e);
		}

		password1 = PwdGenerator
		    .getPassword();
		password2 = password1;

		Role adminRole = RoleLocalServiceUtil
		    .getRole(serviceContext
		        .getCompanyId(), "Administrator");
		List<User> adminUsers = UserLocalServiceUtil
		    .getRoleUsers(adminRole
		        .getRoleId());

		PrincipalThreadLocal
		    .setName(adminUsers
		        .get(0).getUserId());
		PermissionChecker permissionChecker;
		try {
			permissionChecker = PermissionCheckerFactoryUtil
			    .create(adminUsers
			        .get(0));
			PermissionThreadLocal
			    .setPermissionChecker(permissionChecker);
		}
		catch (Exception e) {
			_log
			    .error(e);
		}

		User mappingUser = userService
		    .addUserWithWorkflow(serviceContext
		        .getCompanyId(), autoPassword, password1, password2,
		        autoScreenName, screenName, email, 0L, StringPool.BLANK,
		        LocaleUtil
		            .getDefault(),
		        spn
		            .getFirstName(),
		        spn
		            .getMidName(),
		        spn
		            .getLastName(),
		        0, 0, (gender == 1), birthDateMonth, birthDateDay,
		        birthDateYear, "Citizen", groupIds, organizationIds, roleIds,
		        userGroupIds, new ArrayList<Address>(),
		        new ArrayList<EmailAddress>(), new ArrayList<Phone>(),
		        new ArrayList<Website>(),
		        new ArrayList<AnnouncementsDelivery>(), sendEmail,
		        serviceContext);

		int status = WorkflowConstants.STATUS_INACTIVE;

		mappingUser = userService
		    .updateStatus(mappingUser
		        .getUserId(), status);

		String[] folderNames = new String[] {
		    PortletConstants.DestinationRoot.CITIZEN
		        .toString(),
		    cityName, districtName, wardName, String
		        .valueOf(mappingUser
		            .getUserId())
		};

		String destination = PortletUtil
		    .getDestinationFolder(folderNames);

		serviceContext
		    .setAddGroupPermissions(true);
		serviceContext
		    .setAddGuestPermissions(true);

		FileEntry fileEntry = null;

		if (size > 0 && inputStream != null) {
			// Create person folder
			DLFolder dlFolder = DLFolderUtil
			    .getTargetFolder(mappingUser
			        .getUserId(), serviceContext
			            .getScopeGroupId(),
			        repositoryId, false, 0, destination, StringPool.BLANK,
			        false, serviceContext);

			fileEntry = DLAppServiceUtil
			    .addFileEntry(repositoryId, dlFolder
			        .getFolderId(), sourceFileName, mimeType, title,
			        StringPool.BLANK, StringPool.BLANK, inputStream, size,
			        serviceContext);
		}

		citizen
		    .setAccountStatus(PortletConstants.ACCOUNT_STATUS_REGISTERED);
		citizen
		    .setAddress(address);
		citizen
		    .setAttachFile(fileEntry != null ? fileEntry
		        .getFileEntryId() : 0);
		citizen
		    .setBirthdate(birthDate);
		citizen
		    .setCityCode(cityCode);
		citizen
		    .setCompanyId(serviceContext
		        .getCompanyId());
		citizen
		    .setCreateDate(now);
		citizen
		    .setDistrictCode(districtCode);
		citizen
		    .setEmail(email);
		citizen
		    .setFullName(fullName);
		citizen
		    .setGender(gender);
		citizen
		    .setGroupId(serviceContext
		        .getScopeGroupId());
		citizen
		    .setMappingUserId(mappingUser
		        .getUserId());
		citizen
		    .setModifiedDate(now);
		citizen
		    .setPersonalId(personalId);
		citizen
		    .setTelNo(telNo);
		citizen
		    .setUserId(mappingUser
		        .getUserId());
		citizen
		    .setWardCode(wardCode);
		citizen
		    .setUuid(serviceContext
		        .getUuid());

		return citizenPersistence
		    .update(citizen);
	}

	public Citizen updateCitizen(
	    long citizenId, String address, String cityCode, String districtCode,
	    String wardCode, String cityName, String districtName, String wardName,
	    String telNo, boolean isChangePassWord, String newPassword,
	    String reTypePassword, long repositoryId, ServiceContext serviceContext)
	    throws SystemException, PortalException {

		Citizen citizen = citizenPersistence
		    .findByPrimaryKey(citizenId);

		User mappingUser = userLocalService
		    .getUser(citizen
		        .getMappingUserId());

		Date now = new Date();

		if (mappingUser != null) {
			// Reset password
			if (isChangePassWord) {
				mappingUser = userLocalService
				    .updatePassword(mappingUser
				        .getUserId(), newPassword, reTypePassword, false);
			}

			if ((cityCode != citizen
			    .getCityCode() || districtCode != citizen
			        .getDistrictCode() ||
			    wardCode != citizen
			        .getWardCode()) &&
			    citizen
			        .getAttachFile() > 0) {
				// Move image folder

				String[] newFolderNames = new String[] {
				    PortletConstants.DestinationRoot.CITIZEN
				        .toString(),
				    cityName, districtName, wardName
				};

				String destination = PortletUtil
				    .getDestinationFolder(newFolderNames);

				DLFolder parentFolder = DLFolderUtil
				    .getTargetFolder(mappingUser
				        .getUserId(), serviceContext
				            .getScopeGroupId(),
				        repositoryId, false, 0, destination, StringPool.BLANK,
				        false, serviceContext);

				FileEntry fileEntry = DLAppServiceUtil
				    .getFileEntry(citizen
				        .getAttachFile());

				DLFolderLocalServiceUtil
				    .moveFolder(mappingUser
				        .getUserId(), fileEntry
				            .getFolderId(),
				        parentFolder
				            .getFolderId(),
				        serviceContext);
			}
		}

		citizen
		    .setAddress(address);

		citizen
		    .setCityCode(cityCode);

		citizen
		    .setDistrictCode(districtCode);

		citizen
		    .setModifiedDate(now);

		citizen
		    .setTelNo(telNo);
		citizen
		    .setUserId(mappingUser
		        .getUserId());
		citizen
		    .setWardCode(wardCode);

		return citizenPersistence
		    .update(citizen);

	}

	public Citizen getCitizen(long mappingUserId)
	    throws NoSuchCitizenException, SystemException {

		return citizenPersistence
		    .findByMappingUserId(mappingUserId);
	}

	public Citizen updateStatus(long citizenId, long userId, int accountStatus)
	    throws SystemException, PortalException {

		Citizen citizen = citizenPersistence
		    .findByPrimaryKey(citizenId);

		int userStatus = WorkflowConstants.STATUS_INACTIVE;

		if (accountStatus == PortletConstants.ACCOUNT_STATUS_APPROVED) {
			userStatus = WorkflowConstants.STATUS_APPROVED;
		}

		if (citizen
		    .getMappingUserId() > 0) {
			userLocalService
			    .updateStatus(citizen
			        .getMappingUserId(), userStatus);
		}

		citizen
		    .setUserId(userId);
		citizen
		    .setModifiedDate(new Date());
		citizen
		    .setAccountStatus(accountStatus);

		return citizenPersistence
		    .update(citizen);
	}

	public void sendEmailAddressVerification(
	    User user, String emailAddress, ServiceContext serviceContext)
	    throws PortalException, SystemException {

		if (user
		    .isEmailAddressVerified() && StringUtil
		        .equalsIgnoreCase(emailAddress, user
		            .getEmailAddress())) {

			return;
		}

		Ticket ticket = TicketLocalServiceUtil
		    .addDistinctTicket(user
		        .getCompanyId(), User.class
		            .getName(),
		        user
		            .getUserId(),
		        TicketConstants.TYPE_EMAIL_ADDRESS, emailAddress, null,
		        serviceContext);

		String verifyEmailAddressURL = serviceContext
		    .getPortalURL() + serviceContext
		        .getPathMain() +
		    "/portal/verify_email_address?ticketKey=" + ticket
		        .getKey();

		long plid = serviceContext
		    .getPlid();

		if (plid > 0) {
			Layout layout = LayoutLocalServiceUtil
			    .fetchLayout(plid);

			if (layout != null) {
				Group group = layout
				    .getGroup();

				if (!layout
				    .isPrivateLayout() && !group
				        .isUser()) {
					verifyEmailAddressURL += "&p_l_id=" + serviceContext
					    .getPlid();
				}
			}
		}

		String fromName = PrefsPropsUtil
		    .getString(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_NAME);
		String fromAddress = PrefsPropsUtil
		    .getString(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);

		String toName = user
		    .getFullName();
		String toAddress = emailAddress;

		String subject = PrefsPropsUtil
		    .getContent(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_VERIFICATION_SUBJECT);

		String body = PrefsPropsUtil
		    .getContent(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_VERIFICATION_BODY);

		SubscriptionSender subscriptionSender = new SubscriptionSender();

		subscriptionSender
		    .setBody(body);
		subscriptionSender
		    .setCompanyId(user
		        .getCompanyId());
		subscriptionSender
		    .setContextAttributes("[$EMAIL_VERIFICATION_CODE$]", ticket
		        .getKey(), "[$EMAIL_VERIFICATION_URL$]", verifyEmailAddressURL,
		        "[$REMOTE_ADDRESS$]", serviceContext
		            .getRemoteAddr(),
		        "[$REMOTE_HOST$]", serviceContext
		            .getRemoteHost(),
		        "[$USER_ID$]", user
		            .getUserId(),
		        "[$USER_SCREENNAME$]", user
		            .getScreenName());
		subscriptionSender
		    .setFrom(fromAddress, fromName);
		subscriptionSender
		    .setHtmlFormat(true);
		subscriptionSender
		    .setMailId("user", user
		        .getUserId(), System
		            .currentTimeMillis(),
		        PwdGenerator
		            .getPassword());
		subscriptionSender
		    .setServiceContext(serviceContext);
		subscriptionSender
		    .setSubject(subject);
		subscriptionSender
		    .setUserId(user
		        .getUserId());

		subscriptionSender
		    .addRuntimeSubscribers(toAddress, toName);

		subscriptionSender
		    .flushNotificationsAsync();
	}

	protected void sendEmail(
	    User user, String password, ServiceContext serviceContext)
	    throws SystemException {

		if (!PrefsPropsUtil
		    .getBoolean(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_USER_ADDED_ENABLED)) {

			return;
		}

		String fromName = PrefsPropsUtil
		    .getString(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_NAME);
		String fromAddress = PrefsPropsUtil
		    .getString(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);

		String toName = user
		    .getFullName();
		String toAddress = user
		    .getEmailAddress();

		String subject = PrefsPropsUtil
		    .getContent(user
		        .getCompanyId(), PropsKeys.ADMIN_EMAIL_USER_ADDED_SUBJECT);

		String body = null;

		if (Validator
		    .isNotNull(password)) {
			body = PrefsPropsUtil
			    .getContent(user
			        .getCompanyId(), PropsKeys.ADMIN_EMAIL_USER_ADDED_BODY);
		}
		else {
			body = PrefsPropsUtil
			    .getContent(user
			        .getCompanyId(),
			        PropsKeys.ADMIN_EMAIL_USER_ADDED_NO_PASSWORD_BODY);
		}

		SubscriptionSender subscriptionSender = new SubscriptionSender();

		subscriptionSender
		    .setBody(body);
		subscriptionSender
		    .setCompanyId(user
		        .getCompanyId());
		subscriptionSender
		    .setContextAttributes("[$USER_ID$]", user
		        .getUserId(), "[$USER_PASSWORD$]", password,
		        "[$USER_SCREENNAME$]", user
		            .getScreenName());
		subscriptionSender
		    .setFrom(fromAddress, fromName);
		subscriptionSender
		    .setHtmlFormat(true);
		subscriptionSender
		    .setMailId("user", user
		        .getUserId(), System
		            .currentTimeMillis(),
		        PwdGenerator
		            .getPassword());
		subscriptionSender
		    .setServiceContext(serviceContext);
		subscriptionSender
		    .setSubject(subject);
		subscriptionSender
		    .setUserId(user
		        .getUserId());

		subscriptionSender
		    .addRuntimeSubscribers(toAddress, toName);

		subscriptionSender
		    .flushNotificationsAsync();
	}
	
	public Citizen getCitizen(String email) throws 
	NoSuchCitizenException, SystemException {
		return citizenPersistence.findByEmail(email);
	}
	
	public List<Citizen> getCitizens(int start, int end, OrderByComparator odc)
					throws SystemException {
		return citizenPersistence.findAll(start, end, odc);
	}
	
	public List<Citizen> getCitizens(long groupId, int accountStatus)
					throws SystemException {
		return citizenPersistence.findByG_S(groupId, accountStatus);
	}
	
	public List<Citizen> getCitizens(long groupId, String fullName , int accountStatus)
					throws SystemException {
		return citizenPersistence.findByG_N_S(groupId, fullName, accountStatus);
	}
	
	public List<Citizen> getCitizens(long groupId, String fullName)
					throws SystemException {
		return citizenPersistence.findByG_N(groupId, fullName);
	}
	
	public int countAll() throws SystemException {
		return citizenPersistence.countAll();
	}

	private Log _log = LogFactoryUtil
	    .getLog(CitizenLocalServiceImpl.class
	        .getName());
}
