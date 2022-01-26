package xyz.cssxsh.selenium

internal object RegisterKeys {
    const val USER_CHOICE =
        """HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\Shell\Associations\URLAssociations\https\UserChoice|ProgId"""
    const val EDGE =
        """HKEY_CURRENT_USER\SOFTWARE\Microsoft\Edge\BLBeacon|version"""
    const val CHROME =
        """HKEY_CURRENT_USER\SOFTWARE\Google\Chrome\BLBeacon|version"""
    const val CHROMIUM =
        """HKEY_CURRENT_USER\SOFTWARE\Chromium\BLBeacon|version"""
    const val FIREFOX =
        """HKEY_CURRENT_USER\SOFTWARE\Mozilla\Mozilla Firefox|CurrentVersion"""
}