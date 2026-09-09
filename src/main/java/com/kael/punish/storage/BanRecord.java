package com.kael.punish.storage;

public final class BanRecord {

    public enum Type {
        BAN("永久封禁"),
        TEMPBAN("临时封禁"),
        IP_BAN("IP 永久封禁"),
        TEMP_IP_BAN("IP 临时封禁"),
        LINK_BAN("链式永久封禁"),
        LINK_TEMP_BAN("链式临时封禁");

        private final String chineseName;

        Type(String chineseName) {
            this.chineseName = chineseName;
        }

        public String chineseName() {
            return chineseName;
        }

        public int severity() {
            switch (this) {
                case LINK_BAN:
                case LINK_TEMP_BAN:
                    return 3;
                case IP_BAN:
                case TEMP_IP_BAN:
                    return 2;
                default:
                    return 1;
            }
        }
    }

    private final long id;
    private final String banId;
    private final Type type;
    private final String targetUuid;
    private final String targetName;
    private final String targetNameLower;
    private final String ip;
    private final String reason;
    private final String bannedBy;
    private final long startAt;
    private final Long endAt;
    private final boolean active;

    public BanRecord(long id, String banId, Type type, String targetUuid, String targetName,
                     String targetNameLower, String ip, String reason, String bannedBy,
                     long startAt, Long endAt, boolean active) {
        this.id = id;
        this.banId = banId;
        this.type = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetNameLower = targetNameLower;
        this.ip = ip;
        this.reason = reason;
        this.bannedBy = bannedBy;
        this.startAt = startAt;
        this.endAt = endAt;
        this.active = active;
    }

    public long getId() {
        return id;
    }

    public String getBanId() {
        return banId;
    }

    public Type getType() {
        return type;
    }

    public String getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getTargetNameLower() {
        return targetNameLower;
    }

    public String getIp() {
        return ip;
    }

    public String getReason() {
        return reason;
    }

    public String getBannedBy() {
        return bannedBy;
    }

    public long getStartAt() {
        return startAt;
    }

    public Long getEndAt() {
        return endAt;
    }

    public boolean isPermanent() {
        return endAt == null;
    }
}
