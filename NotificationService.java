@Service
@AllArgsConstructor
public class NotificationService {
    private static final Logger LOGGER = Logger.getLogger(MachineStatusSender.class.getName());
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final FirebaseMessaging firebaseMessaging;
    private final MachineRepository machineRepository;
    private final UserRepository userRepository;
    private final MachineStatusRepository machineStatusRepository;
    private final SentNotificationRepository sentNotificationRepository;

    public void sendFirebaseNotifications(MachineStatus machineStatus) {
        MachineStatus penultimateMachine = machineStatusRepository.findPenultimateRecordByMachineId(machineStatus.getMachineId());
        Optional<Machine> machine = machineRepository.findByMachineId(machineStatus.getMachineId());
        if(machine.isEmpty()){
            LOGGER.log(WARNING,"Machine ID in received machine status is invalid." );
            throw new MachineMonitoringException("Machine ID in received machine status is invalid.", HttpStatus.BAD_REQUEST);
        }
        executorService.execute(new MachineStatusSender(firebaseMessaging, machineStatus, penultimateMachine, machine.get(), userRepository, sentNotificationRepository));
    }

    public ExecutorService getExecutorService(){
        return this.executorService;
    }
}
