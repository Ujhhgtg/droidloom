PRODUCT_PACKAGES += android.hardware.camera.provider-V1-external-service
PRODUCT_COPY_FILES += \
    vendor/droidloom/android/camera/external_camera_config.xml:$(TARGET_COPY_OUT_VENDOR)/etc/external_camera_config.xml \
    vendor/droidloom/android/camera/media_profiles_V1_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    vendor/droidloom/android/camera/android.hardware.droidloom_camera.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.droidloom_camera.xml
