package pl.tw.ksiegowosc.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_api_credentials")
public class UserApiCredentials {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "merit_api_id", length = 100)
    private String meritApiId;

    @Column(name = "merit_api_key", length = 500)
    private String meritApiKey;

    @Column(name = "allegro_client_id", length = 200)
    private String allegroClientId;

    @Column(name = "allegro_client_secret", length = 500)
    private String allegroClientSecret;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMeritApiId() {
        return meritApiId;
    }

    public void setMeritApiId(String meritApiId) {
        this.meritApiId = meritApiId;
    }

    public String getMeritApiKey() {
        return meritApiKey;
    }

    public void setMeritApiKey(String meritApiKey) {
        this.meritApiKey = meritApiKey;
    }

    public String getAllegroClientId() {
        return allegroClientId;
    }

    public void setAllegroClientId(String allegroClientId) {
        this.allegroClientId = allegroClientId;
    }

    public String getAllegroClientSecret() {
        return allegroClientSecret;
    }

    public void setAllegroClientSecret(String allegroClientSecret) {
        this.allegroClientSecret = allegroClientSecret;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
