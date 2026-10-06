package ai.opencode.ide.jetbrains.ui

import ai.opencode.ide.jetbrains.util.OpenCodeCliCompatibility
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.util.Base64
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for configuring OpenCode server connection.
 * Allows user to specify host:port before starting the terminal.
 */
class OpenCodeConnectDialog(
    private val project: Project,
    private val defaultPort: Int,
    private val passwordRequired: Boolean = false
) : DialogWrapper(project, true) {

    data class ConnectionInfo(
        val hostname: String,
        val port: Int,
        val password: String?,
        val useWebInterface: Boolean,
        val customBasePath: String? = null
    )

    private val addressField = JBTextField("127.0.0.1:$defaultPort")
    private val passwordField = JBPasswordField()
    private val rememberPasswordCheckBox = JBCheckBox("Remember password")
    private val basePathField = ComboBox<String>().apply {
        isEditable = true
    }
    
    var hostname: String = "127.0.0.1"
        private set
    var port: Int = defaultPort
        private set
    var password: String? = null
        private set
    var customBasePath: String? = null
        private set
    var useWebInterface: Boolean = false
        private set

    init {
        title = "Connect to OpenCode"
        setOKButtonText("Connect")
        setCancelButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(350), JBUI.scale(180))

        val formPanel = JPanel(GridLayout(7, 1, 0, JBUI.scale(4)))
        val addressLabel = JBLabel("Server address:")
        val passwordLabel = JBLabel(if (passwordRequired) "Server password (required):" else "Server password:")
        val basePathLabel = JBLabel("Custom base path (optional):")

        // Load saved values
        val props = PropertiesComponent.getInstance()
        // Always suggest a new available port by default
        addressField.text = "127.0.0.1:$defaultPort"
        
        // Prefer the IDE credential store, migrating passwords saved by older plugin versions.
        try {
            val savedPassword = PasswordSafe.instance.getPassword(PASSWORD_CREDENTIALS)
            if (!savedPassword.isNullOrEmpty()) {
                passwordField.text = savedPassword
                rememberPasswordCheckBox.isSelected = true
            } else {
                val legacyEncodedPassword = props.getValue(PROP_LEGACY_LAST_PASSWORD, "")
                if (legacyEncodedPassword.isNotBlank()) {
                    val legacyPassword = try {
                        String(Base64.getDecoder().decode(legacyEncodedPassword))
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Could not decode a password saved by an older OpenCode plugin version", e)
                        ""
                    }
                    if (legacyPassword.isNotBlank()) {
                        passwordField.text = legacyPassword
                        rememberPasswordCheckBox.isSelected = true
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not retrieve the remembered OpenCode password", e)
            Messages.showErrorDialog(project, "Could not retrieve the remembered OpenCode password: ${e.message}", "OpenCode")
        }

        // Load saved custom base path
        val savedPath = props.getValue(PROP_CUSTOM_BASE_PATH, "")

        // Populate module paths
        val moduleManager = ModuleManager.getInstance(project)
        val modules = moduleManager.modules
        val paths = modules.mapNotNull { it.guessModuleDir()?.path }.sorted()
        paths.forEach { basePathField.addItem(it) }

        if (savedPath.isNotBlank()) {
            basePathField.item = savedPath
        } else {
            basePathField.selectedIndex = -1
        }

        addressField.toolTipText = "Format: hostname:port (e.g., 127.0.0.1:4096)"
        passwordField.toolTipText = "Used for OPENCODE_PASSWORD and OPENCODE_SERVER_PASSWORD"
        passwordField.emptyText.text = if (passwordRequired) {
            "Required for OpenCode CLI v2"
        } else {
            "Required when the server requests authentication"
        }
        rememberPasswordCheckBox.toolTipText = "Store the password securely in the IDE password safe"
        basePathField.toolTipText = "Override project base path for opencode.exe working directory"

        formPanel.add(addressLabel)
        formPanel.add(addressField)
        formPanel.add(passwordLabel)
        formPanel.add(passwordField)
        formPanel.add(rememberPasswordCheckBox)
        formPanel.add(basePathLabel)
        formPanel.add(basePathField)

        panel.add(formPanel, BorderLayout.CENTER)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = addressField

    override fun doValidate(): ValidationInfo? {
        val input = addressField.text.trim()
        
        if (input.isBlank()) {
            return ValidationInfo("Server address cannot be empty", addressField)
        }
        
        val parts = input.split(":")
        if (parts.size != 2) {
            return ValidationInfo("Invalid format. Expected: hostname:port", addressField)
        }
        
        val host = parts[0].trim()
        val portStr = parts[1].trim()
        
        if (host.isBlank()) {
            return ValidationInfo("Hostname cannot be empty", addressField)
        }
        
        val portNum = portStr.toIntOrNull()
        if (portNum == null || portNum < 1 || portNum > 65535) {
            return ValidationInfo("Port must be a number between 1 and 65535", addressField)
        }

        if (passwordRequired && passwordField.password.isEmpty()) {
            return ValidationInfo("A server password is required for OpenCode CLI v2", passwordField)
        }
        
        return null
    }

    override fun doOKAction() {
        val input = addressField.text.trim()
        val parts = input.split(":")
        hostname = parts[0].trim()
        port = parts[1].trim().toInt()

        val passwordValue = passwordField.password.concatToString()
        password = passwordValue.ifEmpty { null }
        
        val basePathValue = (basePathField.editor.item as? String)?.trim() ?: ""
        customBasePath = basePathValue.ifBlank { null }

        useWebInterface = false

        // Save values for next time
        val props = PropertiesComponent.getInstance()
        props.setValue(PROP_LAST_ADDRESS, "$hostname:$port")
        
        // Save remembered passwords in the IDE's secure credential store.
        try {
            val credentials = if (rememberPasswordCheckBox.isSelected && !password.isNullOrEmpty()) {
                Credentials(null, password)
            } else {
                null
            }
            PasswordSafe.instance.set(PASSWORD_CREDENTIALS, credentials)
            props.unsetValue(PROP_LEGACY_LAST_PASSWORD)
        } catch (e: Exception) {
            logger.warn("Could not save the remembered OpenCode password", e)
            Messages.showErrorDialog(project, "Could not save the OpenCode password securely: ${e.message}", "OpenCode")
            return
        }

        // Save custom base path
        if (!customBasePath.isNullOrBlank()) {
            props.setValue(PROP_CUSTOM_BASE_PATH, customBasePath!!)
        } else {
            props.unsetValue(PROP_CUSTOM_BASE_PATH)
        }

        super.doOKAction()
    }

    companion object {
        private val logger = Logger.getInstance(OpenCodeConnectDialog::class.java)
        private const val PROP_LAST_ADDRESS = "opencode.lastAddress"
        private const val PROP_LEGACY_LAST_PASSWORD = "opencode.lastPassword"
        private const val PROP_CUSTOM_BASE_PATH = "opencode.customBasePath"
        private val PASSWORD_CREDENTIALS = CredentialAttributes("opencode.server.password")
        
        /**
         * Shows the dialog and returns the result.
         * @return ConnectionInfo if user clicked Connect, null if cancelled
         */
        fun show(project: Project, defaultPort: Int, passwordRequired: Boolean = false): ConnectionInfo? {
            val dialog = OpenCodeConnectDialog(project, defaultPort, passwordRequired)
            return if (dialog.showAndGet()) {
                ConnectionInfo(dialog.hostname, dialog.port, dialog.password, dialog.useWebInterface, dialog.customBasePath)
            } else {
                null
            }
        }
    }
}
