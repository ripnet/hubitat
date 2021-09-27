// IMPORT URL: https://raw.githubusercontent.com/ripnet/hubitat/main/drivers/linkind_door_window_sensor.groovy
/**
 *  Copyright 2021 Tom Young (ripnet@gmail.com)
 *
 *  Version: v1.0
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

import hubitat.zigbee.clusters.iaszone.ZoneStatus

metadata {
    definition (name: "Linkind Door/Window Sensor", namespace: "ripnet", author: "Tom Young", importUrl: "https://raw.githubusercontent.com/ripnet/hubitat/main/drivers/linkind_door_window_sensor.groovy") {
        capability "Battery"
        capability "Configuration"
        capability "ContactSensor"
        capability "Health Check"
        capability "Refresh"
        capability "TamperAlert"
        
       
        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0001,0003,0020,0500,0B05", outClusters: "0019", model: "ZB-DoorSensor-D0003", manufacturer: "lk", application: "3D"
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
    
}

def initialize() {
    return configure()
}

// 0x0002 Zone Status

def refresh() {
    if (logEnable) log.warn "Refresh"
    def refreshCmds = zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021) + 
        zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, 0x0002) +
        zigbee.enrollResponse()
    
    return refreshCmds
}

def configure() {
    if (logEnable) log.warn "Configure"   
    
    def cmds = refresh() +
        zigbee.configureReporting(zigbee.IAS_ZONE_CLUSTER, 0x0002, DataType.BITMAP16, 1440, 1440, null) +
        zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, 30, 21600, 0x1) +
        zigbee.enrollResponse()
    
    return cmds
    
}

def ping() {
    zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, 0x0002)
    zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021)
}

def parse(String description) {
    
    if (description.startsWith("catchall")) {
        descMap = zigbee.parseDescriptionAsMap(description)
        if (logEnable) log.debug "catchall Map: '${descMap}'"
        return
    } else if (description.startsWith("zone status")) {
        ZoneStatus zs = zigbee.parseZoneStatus(description)
        if (logEnable) log.debug "Alarm1 Status: '${zs.alarm1Set}'"
        if (logEnable) log.debug "Tamper Status: '${zs.tamperSet}'"
    
        def alarmEvent = createEvent(name: "contact", value: (zs.alarm1Set ? "open" : "closed"))
        def tamperEvent = createEvent(name: "tamper", value: (zs.tamperSet ? "detected" : "clear"))
        return [alarmEvent, tamperEvent]

    } else if (description.startsWith("read attr")) {
        def descMap = zigbee.parseDescriptionAsMap(description)
        
        if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x21 ) {
            BigDecimal battery = Integer.parseInt(descMap.value, 16) / 2.0
            if (logEnable) log.debug "Battery Event - Battery at '${battery}'%"
            return createEvent(name: "battery", value: battery, unit: "%")
        } else if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x20 ) {
            BigDecimal voltage = Integer.parseInt(descMap.value, 16) / 10.0
            if (logEnable) log.debug "Battery Voltage: '${voltage}'V"
            return
        } else if (descMap.clusterInt == zigbee.IAS_ZONE_CLUSTER && descMap.attrInt == 0x2) {
            def value = Integer.parseInt(descMap.value, 16)
            def alarm1Set = value & 1
            def tamperSet = value & 4
            if (logEnable) log.debug "Alarm1 Status: '${alarm1Set}'"
            if (logEnable) log.debug "Tamper Status: '${tamperSet}'"
    
            def alarmEvent = createEvent(name: "contact", value: (alarm1Set ? "open" : "closed"))
            def tamperEvent = createEvent(name: "tamper", value: (tamperSet ? "detected" : "clear"))
            return [alarmEvent, tamperEvent]
        } else {
            if (logEnable) log.debug "read attr Map: '${descMap}'"
            return
        }
    }
    if (logEnable) log.debug "Parsing: '${description}'"
}

