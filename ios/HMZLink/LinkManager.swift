import CoreBluetooth
import Foundation

/// Connects the iPhone to HMZ Play (the car head unit) over Bluetooth Low Energy.
///
/// iOS hides HMZ Play from Settings > Bluetooth because the head unit runs Android 9, which
/// can't advertise the way iOS expects. This app connects to it directly instead, then reads a
/// protected characteristic: that triggers the standard iOS pairing prompt. Once paired, the head
/// unit can use Apple's public notification (ANCS) and media (AMS) services.
///
/// Nothing else is shared: this app has no access to your messages; iOS asks you to allow
/// notification sharing and you can revoke it in Settings > Bluetooth > HMZ Play (i).
final class LinkManager: NSObject, ObservableObject {
    static let serviceUUID = CBUUID(string: "6E8B0001-4D2C-4F2A-9A7E-2B5C9D1E0A01")
    static let pairCharUUID = CBUUID(string: "6E8B0002-4D2C-4F2A-9A7E-2B5C9D1E0A01")
    private static let restoreID = "com.hmz.link.central"
    private static let savedKey = "hmz.peripheral.id"

    enum State: Equatable {
        case bluetoothOff
        case unauthorized
        case searching
        case connecting(String)
        case pairing(String)
        case connected(String)
    }

    @Published private(set) var state: State = .searching
    @Published private(set) var log: [String] = []

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?

    var hasSavedDevice: Bool { savedID != nil }

    private var savedID: UUID? {
        get { UserDefaults.standard.string(forKey: Self.savedKey).flatMap(UUID.init(uuidString:)) }
        set { UserDefaults.standard.set(newValue?.uuidString, forKey: Self.savedKey) }
    }

    override init() {
        super.init()
        central = CBCentralManager(
            delegate: self,
            queue: nil,
            options: [CBCentralManagerOptionRestoreIdentifierKey: Self.restoreID,
                      CBCentralManagerOptionShowPowerAlertKey: true]
        )
    }

    // MARK: - Actions

    func searchAgain() {
        if let p = peripheral { central.cancelPeripheralConnection(p) }
        peripheral = nil
        startScan()
    }

    func forget() {
        if let p = peripheral { central.cancelPeripheralConnection(p) }
        peripheral = nil
        savedID = nil
        note("Autoradio oublié. Pense aussi à l'oublier dans Réglages > Bluetooth.")
        startScan()
    }

    // MARK: - Internals

    private func note(_ s: String) {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        DispatchQueue.main.async {
            self.log.insert("\(f.string(from: Date()))  \(s)", at: 0)
            if self.log.count > 30 { self.log.removeLast() }
        }
    }

    private func reconnectOrScan() {
        guard central.state == .poweredOn else { return }
        if let id = savedID, let p = central.retrievePeripherals(withIdentifiers: [id]).first {
            // A pending connect never times out on iOS: it completes as soon as the car is in range,
            // even with the app in the background. That's the "connects by itself" part.
            attach(p)
            state = .connecting(p.name ?? "HMZ Play")
            central.connect(p, options: nil)
            note("Reconnexion automatique à \(p.name ?? "HMZ Play") en attente")
        } else {
            startScan()
        }
    }

    private func startScan() {
        guard central.state == .poweredOn else { return }
        state = .searching
        central.scanForPeripherals(withServices: [Self.serviceUUID], options: nil)
        note("Recherche de HMZ Play…")
    }

    private func attach(_ p: CBPeripheral) {
        peripheral = p
        p.delegate = self
    }
}

extension LinkManager: CBCentralManagerDelegate {
    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        if let p = (dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral])?.first {
            attach(p)
        }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            if let p = peripheral, p.state == .connected {
                state = .connected(p.name ?? "HMZ Play")
                p.discoverServices([Self.serviceUUID])
            } else {
                reconnectOrScan()
            }
        case .unauthorized:
            state = .unauthorized
        default:
            state = .bluetoothOff
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        central.stopScan()
        let name = peripheral.name ?? (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? "HMZ Play"
        note("Trouvé : \(name) (\(RSSI) dBm)")
        attach(peripheral)
        state = .connecting(name)
        central.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        savedID = peripheral.identifier
        note("Connecté, vérification du jumelage…")
        state = .pairing(peripheral.name ?? "HMZ Play")
        peripheral.discoverServices([Self.serviceUUID])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        note("Échec de connexion : \(error?.localizedDescription ?? "inconnu")")
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { self.reconnectOrScan() }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        note("Déconnecté\(error.map { " : \($0.localizedDescription)" } ?? "")")
        // Ask iOS to reconnect whenever the car comes back in range.
        guard central.state == .poweredOn, savedID != nil else { return }
        state = .connecting(peripheral.name ?? "HMZ Play")
        central.connect(peripheral, options: nil)
    }
}

extension LinkManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let s = peripheral.services?.first(where: { $0.uuid == Self.serviceUUID }) else {
            note("Service HMZ Play introuvable")
            return
        }
        peripheral.discoverCharacteristics([Self.pairCharUUID], for: s)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let c = service.characteristics?.first(where: { $0.uuid == Self.pairCharUUID }) else { return }
        // This characteristic requires encryption: reading it makes iOS show the pairing prompt
        // the first time, then it just succeeds.
        peripheral.readValue(for: c)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            note("Jumelage refusé ou en attente : \(error.localizedDescription)")
            state = .pairing(peripheral.name ?? "HMZ Play")
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { peripheral.readValue(for: characteristic) }
            return
        }
        note("Jumelé et connecté ✔︎")
        state = .connected(peripheral.name ?? "HMZ Play")
    }
}
