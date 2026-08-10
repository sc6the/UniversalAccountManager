package me.proxycracked.universalaccountmanager.auth;

public class Account {
   public static final String TYPE_MS = "ms";
   public static final String TYPE_TOKEN = "token";
   public static final String TYPE_COOKIE = "cookie";
   public static final String TYPE_LAUNCHER = "launcher";
   private String type;
   private String refreshToken;
   private String accessToken;
   private String username;
   private String uuid;
   private long unban;
   private boolean pinned;
   private transient Boolean available;

   public Account(String type, String refreshToken, String accessToken, String username, String uuid, long unban) {
      this.type = type == null ? "ms" : type;
      this.refreshToken = refreshToken == null ? "" : refreshToken;
      this.accessToken = accessToken == null ? "" : accessToken;
      this.username = username == null ? "" : username;
      this.uuid = uuid == null ? "" : uuid;
      this.unban = unban;
   }

   public Account(String refreshToken, String accessToken, String username) {
      this("ms", refreshToken, accessToken, username, "", 0L);
   }

   public String getType() {
      return this.type;
   }

   public String getRefreshToken() {
      return this.refreshToken;
   }

   public String getAccessToken() {
      return this.accessToken;
   }

   public String getUsername() {
      return this.username;
   }

   public String getUuid() {
      return this.uuid;
   }

   public long getUnban() {
      return this.unban;
   }

   public void setType(String type) {
      this.type = type;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public void setUuid(String uuid) {
      this.uuid = uuid;
   }

   public void setUnban(long unban) {
      this.unban = unban;
   }

   public boolean isToken() {
      return "token".equals(this.type) || "launcher".equals(this.type);
   }

   public boolean isCookie() {
      return "cookie".equals(this.type);
   }

   public boolean isLauncher() {
      return "launcher".equals(this.type);
   }

   public boolean isMs() {
      return "ms".equals(this.type);
   }

   public Boolean getAvailable() {
      return this.available;
   }

   public void setAvailable(Boolean available) {
      this.available = available;
   }

   public boolean isPinned() {
      return this.pinned;
   }

   public void setPinned(boolean pinned) {
      this.pinned = pinned;
   }
}
