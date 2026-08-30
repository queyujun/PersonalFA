package com.yingjing.pfa.ui.screens.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.AssetType

@Composable
fun AddTypePickerScreen(onPick: (AssetType) -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close))
            }
            Text(stringResource(R.string.add_asset_title), style = MaterialTheme.typography.titleLarge)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(AssetType.entries.toList(), key = { it.name }) { type ->
                Card(modifier = Modifier.clickable { onPick(type) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(emojiFor(type), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(type.displayRes),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun emojiFor(type: AssetType): String = when (type) {
    AssetType.REAL_ESTATE -> "🏠"
    AssetType.DEPOSIT -> "💰"
    AssetType.A_SHARE -> "📈"
    AssetType.HK_STOCK -> "🇭🇰"
    AssetType.US_STOCK -> "🇺🇸"
    AssetType.ACCOUNT_CASH -> "💵"
    AssetType.GOLD_ETF -> "🪙"
    AssetType.PHYSICAL_GOLD -> "🧈"
    AssetType.BOND_ETF -> "📜"
    AssetType.EQUITY -> "🏢"
    AssetType.CRYPTO -> "₿"
    AssetType.OTC_FUND -> "📊"
    AssetType.MISC -> "📦"
    AssetType.LIABILITY -> "💳"
}
