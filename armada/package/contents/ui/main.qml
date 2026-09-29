import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.components as PlasmaComponents
import org.kde.plasma.plasma5support as Plasma5Support
import org.kde.plasma.plasmoid

PlasmoidItem {
    id: root

    readonly property string ledHelperPath: decodeURIComponent(
        Qt.resolvedUrl("../code/armada-ledctl.py").toString().replace("file://", "")
    )
    readonly property string ledHelper: "/usr/bin/python3 " + shellQuote(ledHelperPath)
    readonly property string ledStatusCommand: ledHelper + " status"
    readonly property string profileHelper: "/usr/bin/armada-power"
    readonly property string profileStatusCommand: profileHelper + " profile"
    readonly property string steamManager: "/usr/bin/busctl --user set-property com.steampowered.SteamOSManager1 /com/steampowered/SteamOSManager1 com.steampowered.SteamOSManager1.PerformanceProfile1 PerformanceProfile s "

    property var ledState: ({
        "available": false,
        "controller_available": false,
        "power": "unknown",
        "zones": {},
        "error": ""
    })
    property bool ledBusy: false
    property bool ledRefreshing: false
    property string activeProfile: ""
    property string profileError: ""
    property bool profileBusy: false
    property bool profileRefreshing: false

    readonly property bool isPowered: ledState.power === "on" || ledState.power === "partial"
    readonly property string ledStateLabel: {
        if (!ledState.available) return i18n("Unavailable")
        if (ledState.power === "on") return i18n("On")
        if (ledState.power === "off") return i18n("Off")
        if (ledState.power === "partial") return i18n("Partially on")
        return i18n("Unknown state")
    }
    readonly property string ledStateIcon: {
        if (!ledState.available) return "dialog-warning"
        if (isPowered) return "preferences-desktop-color"
        return "process-stop"
    }
    readonly property string profileLabel: {
        if (activeProfile === "eco") return "Eco"
        if (activeProfile === "balanced") return "Balanced"
        if (activeProfile === "performance") return "Performance"
        return i18n("Unknown")
    }
    readonly property string ledError: translateLedError(ledState.error || "")

    preferredRepresentation: compactRepresentation
    toolTipMainText: "Armada OS"
    toolTipSubText: ledBusy ? i18n("Applying change…") : ledStateLabel

    function shellQuote(value) {
        return "'" + value.split("'").join("'\"'\"'") + "'"
    }

    function translateLedError(code) {
        if (code === "") return ""
        if (code === "hardware-unavailable") return i18n("RGB lighting hardware is unavailable")
        if (code === "controller-unavailable") return i18n("Decky or Colores is unavailable")
        return i18n("Could not read the RGB lighting state")
    }

    function runLed(command) {
        if (ledExecutable.connectedSources.indexOf(command) !== -1) {
            ledExecutable.disconnectSource(command)
        }
        ledExecutable.connectSource(command)
    }

    function runProfile(command) {
        if (profileExecutable.connectedSources.indexOf(command) !== -1) {
            profileExecutable.disconnectSource(command)
        }
        profileExecutable.connectSource(command)
    }

    function refreshLed() {
        if (ledRefreshing) return
        ledRefreshing = true
        runLed(ledStatusCommand)
    }

    function refreshProfile() {
        if (profileRefreshing) return
        profileRefreshing = true
        runProfile(profileStatusCommand)
    }

    function refreshAll() {
        if (!ledBusy && !ledRefreshing) refreshLed()
        if (!profileBusy && !profileRefreshing) refreshProfile()
    }

    function setPower(enabled) {
        ledBusy = true
        runLed(ledHelper + " set " + (enabled ? "on" : "off"))
    }

    function setProfile(profile) {
        if (profileBusy || profile === activeProfile) return
        profileBusy = true
        profileError = ""
        const labels = {
            "eco": "Eco",
            "balanced": "Balanced",
            "performance": "Performance"
        }
        runProfile(steamManager + labels[profile])
    }

    function normalizeProfile(value) {
        const profile = value.trim().toLowerCase()
        if (profile === "eco" || profile === "balanced" || profile === "performance") {
            return profile
        }
        return ""
    }

    onExpandedChanged: if (expanded) refreshAll()
    Component.onCompleted: refreshAll()

    Timer {
        interval: 2000
        repeat: true
        running: true
        onTriggered: root.refreshAll()
    }

    Plasma5Support.DataSource {
        id: ledExecutable
        engine: "executable"
        connectedSources: []

        onNewData: function(sourceName, data) {
            const stdout = data["stdout"] || ""
            try {
                root.ledState = JSON.parse(stdout.trim())
            } catch (error) {
                root.ledState = {
                    "available": false,
                    "controller_available": false,
                    "power": "unknown",
                    "zones": {},
                    "error": "invalid-response"
                }
            }
            disconnectSource(sourceName)
            if (sourceName === root.ledStatusCommand) {
                root.ledRefreshing = false
                return
            }
            root.ledBusy = false
            root.refreshLed()
        }
    }

    Plasma5Support.DataSource {
        id: profileExecutable
        engine: "executable"
        connectedSources: []

        onNewData: function(sourceName, data) {
            const exitCode = Number(data["exit code"])
            const stdout = (data["stdout"] || "").trim()
            disconnectSource(sourceName)

            if (sourceName === root.profileStatusCommand) {
                root.profileRefreshing = false
                const profile = root.normalizeProfile(stdout)
                if (exitCode === 0 && profile !== "") {
                    root.activeProfile = profile
                    root.profileError = ""
                } else {
                    root.profileError = i18n("Could not read the performance profile")
                }
                return
            }

            root.profileBusy = false
            if (exitCode !== 0) {
                root.profileError = i18n("Could not change the performance profile")
            }
            root.refreshProfile()
        }
    }

    compactRepresentation: MouseArea {
        implicitWidth: Kirigami.Units.iconSizes.smallMedium
        implicitHeight: Kirigami.Units.iconSizes.smallMedium
        hoverEnabled: true
        onClicked: root.expanded = !root.expanded

        Kirigami.Icon {
            anchors.fill: parent
            source: root.ledStateIcon
            opacity: root.ledBusy ? 0.55 : 1.0
        }

        PlasmaComponents.BusyIndicator {
            anchors.centerIn: parent
            width: parent.width * 0.7
            height: width
            running: root.ledBusy
            visible: running
        }
    }

    fullRepresentation: Item {
        implicitWidth: Kirigami.Units.gridUnit * 18
        implicitHeight: content.implicitHeight + Kirigami.Units.largeSpacing * 2

        ColumnLayout {
            id: content
            anchors.fill: parent
            anchors.margins: Kirigami.Units.largeSpacing
            spacing: Kirigami.Units.smallSpacing

            Kirigami.Heading {
                Layout.fillWidth: true
                Layout.bottomMargin: Kirigami.Units.smallSpacing
                level: 2
                text: "Armada OS"
            }

            RowLayout {
                Layout.fillWidth: true

                Kirigami.Icon {
                    Layout.preferredWidth: Kirigami.Units.iconSizes.medium
                    Layout.preferredHeight: Kirigami.Units.iconSizes.medium
                    source: root.ledStateIcon
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 0

                    PlasmaComponents.Label {
                        Layout.fillWidth: true
                        text: i18n("RGB lights")
                        font.bold: true
                    }
                    PlasmaComponents.Label {
                        Layout.fillWidth: true
                        text: root.ledStateLabel
                        opacity: 0.75
                    }
                }

                PlasmaComponents.Switch {
                    checked: root.isPowered
                    enabled: root.ledState.available
                        && root.ledState.controller_available
                        && !root.ledBusy
                    Accessible.name: i18n("Turn RGB lights on or off")
                    onClicked: root.setPower(checked)
                }
            }

            PlasmaComponents.Label {
                Layout.fillWidth: true
                visible: root.ledError !== ""
                text: root.ledError
                color: Kirigami.Theme.negativeTextColor
                wrapMode: Text.WordWrap
            }

            Kirigami.Separator {
                Layout.fillWidth: true
                Layout.topMargin: Kirigami.Units.smallSpacing
                Layout.bottomMargin: Kirigami.Units.smallSpacing
            }

            PlasmaComponents.RadioButton {
                Layout.fillWidth: true
                text: "Eco"
                checked: root.activeProfile === "eco"
                enabled: !root.profileBusy
                Accessible.name: i18n("Activate Eco profile")
                onClicked: root.setProfile("eco")
            }

            PlasmaComponents.RadioButton {
                Layout.fillWidth: true
                text: "Balanced"
                checked: root.activeProfile === "balanced"
                enabled: !root.profileBusy
                Accessible.name: i18n("Activate Balanced profile")
                onClicked: root.setProfile("balanced")
            }

            PlasmaComponents.RadioButton {
                Layout.fillWidth: true
                text: "Performance"
                checked: root.activeProfile === "performance"
                enabled: !root.profileBusy
                Accessible.name: i18n("Activate Performance profile")
                onClicked: root.setProfile("performance")
            }

            PlasmaComponents.Label {
                Layout.fillWidth: true
                Layout.topMargin: Kirigami.Units.smallSpacing
                text: i18n("Active: %1", root.profileLabel)
                opacity: 0.75
            }

            PlasmaComponents.Label {
                Layout.fillWidth: true
                Layout.topMargin: Kirigami.Units.smallSpacing
                visible: root.profileError !== ""
                text: root.profileError
                color: Kirigami.Theme.negativeTextColor
                wrapMode: Text.WordWrap
            }
        }
    }
}
