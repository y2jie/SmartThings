/**
 *  Copyright 2015 Peter Major
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition (name: "MCO Touch Panel S412-EU", namespace: "petermajor", author: "Peter Major") {
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        capability "Configuration"
        capability "Zw Multichannel"

        command "report"

        fingerprint mfr: "015F", prod: "4121", model: "1302"
    }

    simulator {
        status "on":  "command: 2003, payload: FF"
        status "off": "command: 2003, payload: 00"
    }

    tiles {
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821"
            state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff"
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main "switch"
        details (["switch", "refresh"])
    }
}

def parse(String description) {
    log.debug "MCO2-parse {$description}"
    def result = null
    if (description.startsWith("Err")) {
        result = createEvent(descriptionText:description, isStateChange:true)
    } else if (description != "updated") {
        def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x60: 3, 0x8E: 2])
        if (cmd) {
            result = zwaveEvent(cmd)
        }
    }
    log.debug("'$description' parsed to $result")
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    log.debug "MCO2-zwaveEvent-BasicReport {$cmd}"
    if (cmd.value == 0) {
        createEvent(name: "switch", value: "off")
    } else if (cmd.value == 255) {
        createEvent(name: "switch", value: "on")
    }
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    log.debug "MCO2-zwaveEvent-MultiChannelCmdEncap {$cmd}"
    def encapsulatedCommand = cmd.encapsulatedCommand([0x25: 1, 0x20: 1])
    if (encapsulatedCommand) {
        if (state.enabledEndpoints.find { it == cmd.sourceEndPoint }) {
            def formatCmd = ([cmd.commandClass, cmd.command] + cmd.parameter).collect{ String.format("%02X", it) }.join()
            createEvent(name: "epEvent", value: "$cmd.sourceEndPoint:$formatCmd", isStateChange: true, displayed: false, descriptionText: "(fwd to ep $cmd.sourceEndPoint)")
        } else {
            zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
    log.debug("ManufacturerSpecificReport ${cmd.inspect()}")
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    log.debug "MCO2-zwaveEvent-Command {$cmd}"
    createEvent(descriptionText: "$device.displayName: $cmd", isStateChange: true)
}

def report() {
    zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
}

def on() {
    log.debug "MCO2-on all"
    zwave.switchAllV1.switchAllOn().format()
}

def on(endpoint) {
    log.debug "MCO2-on $endpoint"
    encap(zwave.basicV1.basicSet(value: 0xFF), endpoint).format()
}

def off() {
    log.debug "MCO2-off all"
    zwave.switchAllV1.switchAllOff().format()
}

def off(endpoint) {
    log.debug "MCO2-off $endpoint"
    encap(zwave.basicV1.basicSet(value: 0x00), endpoint).format()
}

def refresh() {
    log.debug "MCO2-refresh"

    def cmds = []
    cmds << encap(zwave.basicV1.basicGet(), 1).format()
    cmds << encap(zwave.basicV1.basicGet(), 2).format()
        
    log.debug "MCO2-refresh-cmds {$cmds}"
    delayBetween(cmds, 1000)
}

def poll(){
    refresh()
}

// currently hard-coded to two button switch
def configure() {
    log.debug "MCO2-configure"
    
    enableEpEvents("1,2")

    delayBetween([
        zwave.associationV1.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId).format(),
        zwave.associationV1.associationSet(groupingIdentifier: 2, nodeId: zwaveHubNodeId).format(),
        zwave.associationV1.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format()
    ])
}

def enableEpEvents(enabledEndpoints) {
    log.debug "MCO2-enabledEndpoints"
    state.enabledEndpoints = enabledEndpoints.split(",").findAll()*.toInteger()
    null
}

private encap(cmd, endpoint) {
    log.debug "MCO2-encap $endpoint {$cmd}"
    zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:endpoint, destinationEndPoint:endpoint).encapsulate(cmd)
}