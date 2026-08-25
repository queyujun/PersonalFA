package com.yingjing.pfa.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.core.security.BiometricAuthenticator
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.ui.theme.BlueLightMode

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val deviceHasBiometric = remember { BiometricAuthenticator.isAvailable(context) }
    val showBiometric = state.mode == AuthMode.Login && deviceHasBiometric &&
        state.lastUserId != null && state.biometricEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(BlueLightMode, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("盈", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text("盈景私助", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "您的个人资产全景管家",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        TabRow(selectedTabIndex = state.mode.ordinal, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = state.mode == AuthMode.Login,
                onClick = { viewModel.setMode(AuthMode.Login) },
                text = { Text("登录") },
            )
            Tab(
                selected = state.mode == AuthMode.Register,
                onClick = { viewModel.setMode(AuthMode.Register) },
                text = { Text("注册") },
            )
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.mode == AuthMode.Register) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text("确认密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "默认计价货币",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Currency.entries.forEach { currency ->
                    FilterChip(
                        selected = state.defaultCurrency == currency,
                        onClick = { viewModel.onCurrencyChange(currency) },
                        label = { Text("${currency.symbol} ${currency.label}") },
                    )
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = viewModel::submit,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text(if (state.mode == AuthMode.Login) "登 录" else "注 册")
            }
        }

        if (showBiometric) {
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = {
                    (context as? FragmentActivity)?.let { activity ->
                        BiometricAuthenticator.authenticate(
                            activity = activity,
                            onSuccess = { viewModel.loginWithBiometric() },
                            onError = {},
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("使用指纹 / 面容登录" + (state.lastUsername?.let { "（$it）" } ?: ""))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "🔐 账户与数据仅保存在本机（加密存储），支持多用户。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
    }
}
