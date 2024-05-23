@Transactional
@AllArgsConstructor
public class MachineStatusSender implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(MachineStatusSender.class.getName());
    private final FirebaseMessaging firebaseMessaging;
    private final MachineStatus machineStatus;
    private final MachineStatus penultimateMachineStatus;
    private final Machine machine;
    private final UserRepository userRepository;
    private final SentNotificationRepository sentNotificationRepository;

    public void run() {
        boolean alreadyNotifiedAlarmsRemoved = removeAlreadyNotifiedAlarms();
        if (!alreadyNotifiedAlarmsRemoved && !machineStatus.getStatus().getAlarmDetails().isEmpty()) {
            String title = buildMachineStatusTitle();
            List<User> usersWithoutBlacklistedMachines = filterUsersWithoutBlacklistedMachines();
            List<Map<User, List<Status.AlarmDetails>>> usersToNotify = getUsersToNotifyWithFilteredAlarms(usersWithoutBlacklistedMachines);

            usersToNotify.forEach(map -> {
                User user = map.keySet().iterator().next();
                List<Status.AlarmDetails> alarmDetails = map.get(user);
                user.getUserConfig().getFirebaseNotificationTokens().forEach(token -> {
                    String message = buildMachineStatusMessage(alarmDetails);
                    Message firebaseMessage = buildFirebaseMessage(title, token, message);
                    SentNotification sentNotification = new SentNotification(firebaseMessage, user);
                    boolean notificationSent = true;
                    try {
                        LOGGER.log(INFO, "Sending message to user: {0}", user.getEmail());
                        LOGGER.log(INFO, "Message: {0}", firebaseMessage);
                        firebaseMessaging.send(firebaseMessage);
                    } catch (FirebaseMessagingException e) {
                        notificationSent = false;
                        LOGGER.log(WARNING, "Error sending message to user: {0}", user.getEmail());
                    }
                    if(notificationSent){
                        LOGGER.log(INFO, "Sending message was successful. Saving message and user in NotificationSent collection.");
                        sentNotificationRepository.save(sentNotification);
                    }
                });
            });
        }
        else {
            LOGGER.log(INFO, "User already received a message for those alarm details: {0}", penultimateMachineStatus.getStatus().getAlarmDetails());
        }
    }

    private Message buildFirebaseMessage(String title, String token, String message) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(message).build())
                .build();
    }

    /**
     * Returns a list of users to notify with their filtered alarms.
     * <p>
     * This method filters out the alarms that are present in their respective blacklists.
     * It creates a map for each user containing the filtered alarm details and adds it to a list.
     * The resulting list contains mappings of users to their filtered alarm details.
     *
     * @param usersWithoutBlacklistedMachines The list of users without blacklisted machines.
     * @return A list of maps, where each map contains a user and their filtered alarm details.
     */
    private List<Map<User, List<Status.AlarmDetails>>> getUsersToNotifyWithFilteredAlarms(List<User> usersWithoutBlacklistedMachines) {
        return usersWithoutBlacklistedMachines
                .parallelStream()
                .map(user -> {
                    List<Status.AlarmDetails> alarmDetails = Status.AlarmDetails.copyAlarmDetails(machineStatus.getStatus().getAlarmDetails());
                    alarmDetails.removeIf(alarmDetail -> checkIfAlarmIsInBlacklist(user, alarmDetail));

                    Map<User, List<Status.AlarmDetails>> userAlarmDetailsMap = new HashMap<>();
                    userAlarmDetailsMap.put(user, alarmDetails);
                    return userAlarmDetailsMap;
                })
                .toList();
    }

    private boolean checkIfAlarmIsInBlacklist(User user, Status.AlarmDetails alarmDetail) {
        return user
                .getUserConfig()
                .getBlacklistAlarms()
                .stream()
                .anyMatch(alarm -> alarm.getAlarmId().equals(alarmDetail.getAlarmId()));
    }


    private List<User> filterUsersWithoutBlacklistedMachines() {
        return userRepository
                .findByCompanyId(Utils.stringToObjectId(machine.getCompanyId()))
                .parallelStream()
                .filter(user -> {
                    if (Objects.isNull(user.getUserConfig()) || user.getUserConfig().getFirebaseNotificationTokens().isEmpty()) {
                        return false;
                    }
                    boolean blacklistedMachine = user
                            .getUserConfig()
                            .getBlacklistMachines()
                            .stream()
                            .anyMatch(machine -> machine.getMachineId().equals(machineStatus.getMachineId()));
                    return !blacklistedMachine;
                })
                .toList();
    }


    private String buildMachineStatusMessage(List<Status.AlarmDetails> alarmDetails) {
        if (machineStatus.getStatus().getAlarmDetails().isEmpty()) {
            return "";
        }
        String lastAlarms = "";
        StringBuilder errorStringBuilder = new StringBuilder();
        errorStringBuilder.append("ERR:\n");
        int numberOfAlarms = alarmDetails.size();
        if (numberOfAlarms > 3) {
            lastAlarms = lastAlarms + "+ " + (numberOfAlarms - 3);
            numberOfAlarms = 3;
        }
        for (int i = 0; i < numberOfAlarms; i++) {
            errorStringBuilder
                    .append(alarmDetails.get(i).getAlarmId())
                    .append(" | ")
                    .append(alarmDetails.get(i).getAlarmDescription())
                    .append("\n");
            if (i == 2) {
                errorStringBuilder.append(lastAlarms);
            }
        }
        return errorStringBuilder.toString();
    }

    private boolean removeAlreadyNotifiedAlarms() {
        if (penultimateMachineStatus == null) {
            return false;
        }
        List<Status.AlarmDetails> alarmDetails = machineStatus.getStatus().getAlarmDetails();
        List<Status.AlarmDetails> penultimateDetails = penultimateMachineStatus.getStatus().getAlarmDetails();

        return alarmDetails.removeIf(alarmDetail ->
                penultimateDetails
                        .stream()
                        .anyMatch(penultimateDetail -> penultimateDetail.getAlarmId().equals(alarmDetail.getAlarmId()))
        );
    }

        private String buildMachineStatusTitle () {
            var greenLight = machineStatus.getStatus().getGreenLight();
            var yellowLight = machineStatus.getStatus().getYellowLight();
            var redLight = machineStatus.getStatus().getRedLight();
            var blueLight = machineStatus.getStatus().getBlueLight();

            boolean allLightsOff = greenLight.equals(SignalLight.OFF)
                    && yellowLight.equals(SignalLight.OFF)
                    && redLight.equals(SignalLight.OFF)
                    && blueLight.equals(SignalLight.OFF);

            String title = machine.getName() + " | ";

            if (greenLight.equals(SignalLight.ON)) title += "🟢";
            if (greenLight.equals(SignalLight.BLINKING)) title += "🟢(blinking)";
            if (yellowLight.equals(SignalLight.ON)) title += "🟡";
            if (yellowLight.equals(SignalLight.BLINKING)) title += "🟡(blinking)";
            if (redLight.equals(SignalLight.ON)) title += "🔴";
            if (redLight.equals(SignalLight.BLINKING)) title += "🔴(blinking)";
            if (blueLight.equals(SignalLight.ON)) title += "🔵";
            if (blueLight.equals(SignalLight.BLINKING)) title += "🔵(blinking)";
            if (allLightsOff) title += "⚫";
            return title;
        }
    }

