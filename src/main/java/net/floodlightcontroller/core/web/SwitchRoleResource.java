/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.core.web;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.dsc.cluster.IClusterService;
import net.dsc.cluster.model.ControllerModel;
import net.dsc.cluster.model.SwitchConnectModel;
import net.dsc.hazelcast.IHazelcastService;
import net.dsc.hazelcast.message.RoleMessage;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;

public class SwitchRoleResource extends ServerResource {

	protected static Logger log = LoggerFactory
			.getLogger(SwitchRoleResource.class);

	private static final String STR_ROLE_PREFIX = "ROLE_"; /*
															 * enum
															 * OFControllerRole
															 * is ROLE_XXX, so
															 * trim the ROLE_
															 * when printing
															 */
	private static final String STR_ROLE_MASTER = "MASTER"; /*
															 * these are all
															 * assumed uppercase
															 * within this class
															 */
	private static final String STR_ROLE_SLAVE = "SLAVE";
	private static final String STR_ROLE_EQUAL = "EQUAL";
	private static final String STR_ROLE_OTHER = "OTHER";

	@Get("json")
	public HashMap<String, HashMap<String, String>> getRole() {
		IOFSwitchService switchService = (IOFSwitchService) getContext()
				.getAttributes().get(IOFSwitchService.class.getCanonicalName());

		IClusterService clusterService = (IClusterService) getContext()// 獲取服務
				.getAttributes().get(IClusterService.class.getCanonicalName());

		String switchId = (String) getRequestAttributes().get(
				CoreWebRoutable.STR_SWITCH_ID);

		HashMap<String, HashMap<String, String>> model = new HashMap<String, HashMap<String, String>>();// 添加加數據結構
		MultiMap<ControllerModel, SwitchConnectModel> ControllerMappingRole = clusterService
				.getControllerMappingSwitch();

		if (switchId.equalsIgnoreCase(CoreWebRoutable.STR_ALL)) {// 判斷查詢字段是否为ALL

			for (IOFSwitch sw : switchService.getAllSwitchMap().values()) {// 遍歷所有交換機
				switchId = sw.getId().toString();
				HashMap<String, String> ControllerRole = new HashMap<String, String>();
				for (ControllerModel controller : ControllerMappingRole
						.keySet()) {
					Collection<SwitchConnectModel> switches = ControllerMappingRole// 遍歷控制器和交換機關係map
							.get(controller);
					Iterator<SwitchConnectModel> it = switches.iterator();// 遍歷switch
					while (it.hasNext()) {
						SwitchConnectModel singleSwitch = it.next();
						if (singleSwitch.getDpid().equals(switchId)) {// 有switchId則添加
							ControllerRole.put(controller.getControllerIp(),
									singleSwitch.getRole());
						}
					}
				}

				model.put(switchId, ControllerRole);
			}
			return model;
		} else {

			try {
				DatapathId dpid = DatapathId.of(switchId);
				IOFSwitch sw = switchService.getSwitch(dpid);
				HashMap<String, String> ReturnMessage = new HashMap<String, String>();
				if (sw == null) {

					ReturnMessage.put("ERROR",
							"Switch Manager could not locate switch DPID "
									+ dpid.toString());
					model.put("error", ReturnMessage);
					return model;
				} else {// 添加單個
					for (ControllerModel controller : ControllerMappingRole
							.keySet()) {
						Collection<SwitchConnectModel> switches = ControllerMappingRole
								.get(controller);
						Iterator<SwitchConnectModel> it = switches.iterator();
						while (it.hasNext()) {
							SwitchConnectModel singleSwitch = it.next();
							if (singleSwitch.getDpid().equals(switchId)) {
								ReturnMessage.put(controller.getControllerIp(),
										singleSwitch.getRole());
							}
						}
					}

					model.put(dpid.toString(), ReturnMessage);
					return model;
				}
			} catch (Exception e) {
				HashMap<String, String> ErrorMessage = new HashMap<String, String>();
				ErrorMessage.put("ERROR", "Could not parse switch DPID "
						+ switchId);
				model.put("ERROR", ErrorMessage);
				return model;
			}
		}
	}

	/* for some reason @Post("json") isn't working here... */
	@Post
	public Map<String, String> setRole(String json) {
		
		IFloodlightProviderService floodlightProvider = (IFloodlightProviderService) getContext()
				.getAttributes().get(
						IFloodlightProviderService.class.getCanonicalName());

		IClusterService clusterService = (IClusterService) getContext()// 獲取cluster服務
				.getAttributes().get(IClusterService.class.getCanonicalName());

		IHazelcastService hazelcastService = (IHazelcastService) getContext().// 获取hazelcast服务
				getAttributes().get(IHazelcastService.class.getCanonicalName());

		IOFSwitchService switchService = (IOFSwitchService) getContext() // 获取switch服务
				.getAttributes().get(IOFSwitchService.class.getCanonicalName());

		MultiMap<ControllerModel, SwitchConnectModel> ControllerMappingRole = clusterService
				.getControllerMappingSwitch();

		IMap<String, ControllerModel> controllers = clusterService
				.getControllers();

		Map<String, String> masterMap = clusterService.getMasterMap();

		Map<String, String> retValue = new HashMap<String, String>();// 返回消息

		String localId = floodlightProvider.getControllerModel()
				.getControllerId();

		String switchId = (String) getRequestAttributes().get(
				CoreWebRoutable.STR_SWITCH_ID);
		List<String> sortList = clusterService.getSortedControllerLoad();
		boolean isControllerId = false;
		boolean isSwitchId = false;// 判断交换机是否存在
		boolean switchHasMaster = false;// 判断交换机是否有master

		boolean isControllerMasterSwitch = false;// 判断请求控制器是否是请求交换机的master
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp = null;
		String role = null;
		String controllerId = null;

		try {
			jp = f.createJsonParser(json);
			while (jp.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jp.getCurrentName();
				if ("controllerId".equals(fieldName)) {
					jp.nextToken();
					controllerId = jp.getText();

				}

				if ("role".equals(fieldName)) {
					jp.nextToken();
					role = jp.getText();

				}

			}

		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			retValue.put("error", "can not parse json" + json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			retValue.put("error", "can not parse json" + json);
		}

		if (controllerId == null || role == null) {// 解析错误

			retValue.put("error", "can not parse json" + json);
			return retValue;

		}
		

		isSwitchId = isSwitchExist(switchId, ControllerMappingRole);// 判断switchId是否存在

		for (ControllerModel controller : ControllerMappingRole.keySet()) {// 判断controllerId是否存在
			System.out.println(controller.getControllerId());
			if (controller.getControllerId().equals(controllerId)) {
				isControllerId = true;
				break;
			}
		}
		for (String dpid : masterMap.keySet()) {// 判断交换机是否有主
			if (dpid.equals(switchId)) {
				switchHasMaster = true;
			}
		}
		
		if (isControllerId && isSwitchId) {// 如果交换机和控制器都存在
			DatapathId dpid = DatapathId.of(switchId);// 得到请求交换机机id
			IOFSwitch sw = switchService.getSwitch(dpid);// 得到交换机
			OFControllerRole controllerRole = parseRole(role);// 解析role
			System.out.println(masterMap.values());
			if (switchHasMaster) {// 如果交换机有主

				isControllerMasterSwitch = masterMap.get(switchId).equals(
						controllerId);

				String masterControllerId = masterMap.get(switchId).toString();

				if (controllerId.equals(localId)) {// 判断请求id是否为本地id

					switch (controllerRole) {

					default:

						retValue.put("error", "not support role");
						return retValue;

					case ROLE_MASTER:// 如果请求role为master

						if (isControllerMasterSwitch) {// 如果本地控制器是交换机的master
							retValue.put("sorry",
									"the controller already have been the master of switch");
							return retValue;
						} else {// 如果本地控制器原来是交换机的slave
							// 让自己成为master
							sw.writeRequest(sw.getOFFactory()

							.buildRoleRequest().setGenerationId(U64.ZERO)
									.setRole(controllerRole).build());

							hazelcastService.publishRoleMessage(
									new RoleMessage("SLAVE", switchId),
									masterControllerId);
							// 让原来的master成为slave
							retValue.put("success", "请求已发送");
							return retValue;

						}

					case ROLE_SLAVE:// 如果请求role为slave

						if (isControllerMasterSwitch) {// 如果请求控制器是交换机的master
							System.out.println("本地控制器是交换机master");

							// selectMaster(sortList, ControllerMappingRole,
							// controllers, switchId, hazelcastService);//重新选主

							sw.writeRequest(sw
									.getOFFactory()
									// 让自己成为slave
									.buildRoleRequest()
									.setGenerationId(U64.ZERO)
									.setRole(controllerRole).build());

						} else {// 如果请求控制器是交换机的slave，则不用做任何变化
							
						}
						retValue.put("ok", "成为slave");
						return retValue;
					

					}

				} else {// 如果请求的控制器ID不是本地ID

					switch (controllerRole) {
					case ROLE_SLAVE:// 请求角色为slave

						hazelcastService.publishRoleMessage(new RoleMessage(
								"SLAVE", switchId), controllerId);// 让请求控制器变为slave

						retValue.put("ok", "slave请求已发送");
						return retValue;

					case ROLE_MASTER:// 请求角色为master
						
						if (masterControllerId.equals(localId)) {// 如果本地控制器是该交换机的master

							sw.writeRequest(sw
									.getOFFactory()
									// 让自己成为slave

									.buildRoleRequest()
									.setGenerationId(U64.ZERO)
									.setRole(parseRole("SLAVE")).build());
						} else {

							if (isControllerMasterSwitch) {// 如果请求控制器是请求交换机master
								retValue.put("sorry",
										"the controller already have been the master of switch");
								return retValue;
							}
						
						}
						hazelcastService.publishRoleMessage(new RoleMessage(
								"MASTER", switchId), controllerId);// 让请求控制器变为master
						retValue.put("ok", "master请求已发送");
						return retValue;
					
					default:
						retValue.put("error", "not support role");
						return retValue;

					}

				}

			} else {// 如果交换机无主
				System.out.println("现在无主");
				switch (controllerRole) {
				default:
					retValue.put("error", "not support role");
					return retValue;
				case ROLE_SLAVE:

					hazelcastService.publishRoleMessage(new RoleMessage(
							"SLAVE", switchId), controllerId);// 让请求控制器变为slave
					retValue.put("ok", "请求已发送");
					return retValue;

				case ROLE_MASTER:
					Collection<SwitchConnectModel> switches = ControllerMappingRole
							.get(controllers.get(controllerId));
					Iterator<SwitchConnectModel> it = switches.iterator();// 遍歷switch
					while (it.hasNext()) {
						SwitchConnectModel singleSwitch = it.next();
						if (singleSwitch.getDpid().equals(switchId)) {
							hazelcastService.publishRoleMessage(
									new RoleMessage("MASTER", switchId),
									controllerId);// 让请求控制器变为master
							retValue.put("ok", "请求已发送");
							return retValue;
						}
					}

					retValue.put("SORRY", "此控制器没有连接此交换机" + json);
					return retValue;
					

				}
			}

		} else {
			retValue.put("error", "controllerId or switchId is not right");
		}

		retValue.put(controllerId, role);

		return retValue;

		/*
		 * try { try { jp = f.createJsonParser(json); } catch (IOException e) {
		 * e.printStackTrace(); }
		 * 
		 * jp.nextToken(); if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
		 * throw new IOException("Expected START_OBJECT"); }
		 * 
		 * while (jp.nextToken() != JsonToken.END_OBJECT) { if
		 * (jp.getCurrentToken() != JsonToken.FIELD_NAME) { throw new
		 * IOException("Expected FIELD_NAME"); }
		 * 
		 * String n = jp.getCurrentName().toLowerCase(); jp.nextToken();
		 * 
		 * switch (n) { case CoreWebRoutable.STR_ROLE: role = jp.getText();
		 * 
		 * if (switchId.equalsIgnoreCase(CoreWebRoutable.STR_ALL)) { for
		 * (IOFSwitch sw : switchService.getAllSwitchMap() .values()) {
		 * List<SetConcurrentRoleThread> activeThreads = new
		 * ArrayList<SetConcurrentRoleThread>(
		 * switchService.getAllSwitchMap().size());
		 * List<SetConcurrentRoleThread> pendingRemovalThreads = new
		 * ArrayList<SetConcurrentRoleThread>(); SetConcurrentRoleThread t; t =
		 * new SetConcurrentRoleThread(sw, parseRole(role));
		 * activeThreads.add(t); t.start();
		 * 
		 * // Join all the threads after the timeout. Set a // hard timeout //
		 * of 12 seconds for the threads to finish. If the // thread has not //
		 * finished the switch has not replied yet and // therefore we won't //
		 * add the switch's stats to the reply. for (int iSleepCycles = 0;
		 * iSleepCycles < 12; iSleepCycles++) { for (SetConcurrentRoleThread
		 * curThread : activeThreads) { if (curThread.getState() ==
		 * State.TERMINATED) { retValue.put( curThread.getSwitch().getId()
		 * .toString(), (curThread.getRoleReply() == null ?
		 * "Error communicating with switch. Role not changed." : curThread
		 * .getRoleReply() .getRole() .toString() .replaceFirst(
		 * STR_ROLE_PREFIX, ""))); pendingRemovalThreads.add(curThread); } }
		 * 
		 * // remove the threads that have completed the // queries to the
		 * switches for (SetConcurrentRoleThread curThread :
		 * pendingRemovalThreads) { activeThreads.remove(curThread); } // clear
		 * the list so we don't try to double // remove them
		 * pendingRemovalThreads.clear();
		 * 
		 * // if we are done finish early so we don't // always get the worst
		 * case if (activeThreads.isEmpty()) { break; }
		 * 
		 * // sleep for 1 s here try { Thread.sleep(1000); } catch
		 * (InterruptedException e) { log.error(
		 * "Interrupted while waiting for role replies", e);
		 * retValue.put("ERROR",
		 * "Thread sleep interrupted while waiting for role replies."); }
		 * 
		 * } } } else { Must be a specific switch DPID then. try { DatapathId
		 * dpid = DatapathId.of(switchId); IOFSwitch sw =
		 * switchService.getSwitch(dpid); if (sw == null) {
		 * retValue.put("ERROR", "Switch Manager could not locate switch DPID "
		 * + dpid.toString()); } else { OFRoleReply reply = setSwitchRole(sw,
		 * parseRole(role)); retValue.put( sw.getId().toString(), (reply == null
		 * ? "Error communicating with switch. Role not changed." :
		 * reply.getRole() .toString() .replaceFirst( STR_ROLE_PREFIX, ""))); }
		 * } catch (Exception e) { retValue.put("ERROR",
		 * "Could not parse switch DPID " + switchId); } } break; default:
		 * retValue.put("ERROR", "Unrecognized JSON key."); break; } } } catch
		 * (IOException e) { e.printStackTrace(); retValue.put("ERROR",
		 * "Caught IOException while parsing JSON POST request in role request."
		 * ); }
		 */

	}

	/*private void selectMaster(
			List<String> sortList,
			MultiMap<ControllerModel, SwitchConnectModel> ControllerMappingRole,
			IMap<String, ControllerModel> controllers, String switchId,
			IHazelcastService hazelcastService) {
		for (String availableControllerId : sortList) {
			Collection<SwitchConnectModel> switches = ControllerMappingRole
					.get(controllers.get(availableControllerId));
			Iterator<SwitchConnectModel> it = switches.iterator();// 遍歷switch
			while (it.hasNext()) {
				SwitchConnectModel singleSwitch = it.next();
				if (singleSwitch.getDpid().equals(switchId)) {
					hazelcastService.publishRoleMessage(new RoleMessage(
							"MASTER", switchId), availableControllerId);// 发master消息给合适的用户
				}
			}
		}
	}*/

	private boolean isSwitchExist(String switchId,
			MultiMap<ControllerModel, SwitchConnectModel> ControllerMappingRole) {
		for (ControllerModel controllerModel : ControllerMappingRole.keySet()) {
			Collection<SwitchConnectModel> switches = ControllerMappingRole
					.get(controllerModel);
			Iterator<SwitchConnectModel> it = switches.iterator();// 遍歷switch
			while (it.hasNext()) {
				SwitchConnectModel singleSwitch = it.next();
				if (singleSwitch.getDpid().equals(switchId)) {
					return true;
				}
			}
		}

		return false;

	}

	/*protected class SetConcurrentRoleThread extends Thread {
		private OFRoleReply switchReply;
		private OFControllerRole role;
		private IOFSwitch sw;

		public SetConcurrentRoleThread(IOFSwitch sw, OFControllerRole role) {
			this.sw = sw;
			this.switchReply = null;
			this.role = role;
		}

		public OFRoleReply getRoleReply() {
			return switchReply;
		}

		public IOFSwitch getSwitch() {
			return sw;
		}

		public OFControllerRole getRole() {
			return role;
		}

		@Override
		public void run() {
			switchReply = setSwitchRole(sw, role);
		}
	}*/

	/*private static OFRoleReply setSwitchRole(IOFSwitch sw, OFControllerRole role) {
		try {
			if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_12) < 0) {
				OFNiciraControllerRole nrole;
				switch (role) {
				case ROLE_EQUAL:
					nrole = OFNiciraControllerRole.ROLE_OTHER;
					log.warn("Assuming EQUAL as OTHER for Nicira role request.");
					break;
				case ROLE_MASTER:
					nrole = OFNiciraControllerRole.ROLE_MASTER;
					break;
				case ROLE_SLAVE:
					nrole = OFNiciraControllerRole.ROLE_SLAVE;
					break;
				case ROLE_NOCHANGE:
					log.error("Nicira extension does not support NOCHANGE role. Thus, we won't change the role.");
					return OFFactories.getFactory(OFVersion.OF_13)
							.buildRoleReply().setRole(sw.getControllerRole())
							.setGenerationId(U64.ZERO).build();
				default:
					log.error("Impossible to have anything other than MASTER, OTHER, or SLAVE for Nicira role.");
					return OFFactories.getFactory(OFVersion.OF_13)
							.buildRoleReply().setRole(sw.getControllerRole())
							.setGenerationId(U64.ZERO).build();
				}

				ListenableFuture<OFNiciraControllerRoleReply> future = sw
						.writeRequest(sw.getOFFactory()
								.buildNiciraControllerRoleRequest()
								.setRole(nrole).build());
				OFNiciraControllerRoleReply nreply = future.get(10,
						TimeUnit.SECONDS);
				if (nreply != null) {
					
					 * Turn the OFControllerRoleReply into a
					 * OFNiciraControllerRoleReply
					 
					switch (nreply.getRole()) {
					case ROLE_MASTER:
						return OFFactories.getFactory(OFVersion.OF_13)
								.buildRoleReply()
								.setRole(OFControllerRole.ROLE_MASTER)
								.setGenerationId(U64.ZERO).build();
					case ROLE_OTHER:
						return OFFactories.getFactory(OFVersion.OF_13)
								.buildRoleReply()
								.setRole(OFControllerRole.ROLE_EQUAL)
								.setGenerationId(U64.ZERO).build();
					case ROLE_SLAVE:
						return OFFactories.getFactory(OFVersion.OF_13)
								.buildRoleReply()
								.setRole(OFControllerRole.ROLE_SLAVE)
								.setGenerationId(U64.ZERO).build();
					default:
						log.error(
								"Impossible to have anything other than MASTER, OTHER, or SLAVE for Nicira role: {}.",
								nreply.getRole().toString());
						break;
					}
				} else {
					log.error(
							"Did not receive Nicira role reply for switch {}.",
							sw.getId().toString());
				}
			} else {
				ListenableFuture<OFRoleReply> future = sw.writeRequest(sw
						.getOFFactory().buildRoleRequest()
						.setGenerationId(U64.ZERO).setRole(role).build());
				return future.get(10, TimeUnit.SECONDS);
			}
		} catch (Exception e) {
			log.error("Failure setting switch {} role to {}.", sw.toString(),
					role.toString());
			log.error(e.getMessage());
		}
		return null;
	}*/

	private static OFControllerRole parseRole(String role) {
		if (role == null || role.isEmpty()) {
			return OFControllerRole.ROLE_NOCHANGE;
		}

		role = role.toUpperCase();

		if (role.contains(STR_ROLE_MASTER)) {
			return OFControllerRole.ROLE_MASTER;
		} else if (role.contains(STR_ROLE_SLAVE)) {
			return OFControllerRole.ROLE_SLAVE;
		} else if (role.contains(STR_ROLE_EQUAL)
				|| role.contains(STR_ROLE_OTHER)) {
			return OFControllerRole.ROLE_EQUAL;
		} else {
			return OFControllerRole.ROLE_NOCHANGE;
		}
	}
}
