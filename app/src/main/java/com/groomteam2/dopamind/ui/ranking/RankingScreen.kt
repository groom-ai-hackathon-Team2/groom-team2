package com.groomteam2.dopamind.ui.ranking

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groomteam2.dopamind.R
import com.groomteam2.dopamind.di.ServiceLocator
import com.groomteam2.dopamind.ui.theme.BrandPurple
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 친구 랭킹(시연용 mock).
 *
 * - assets/mock_friends.json 에서 6명 데이터 로드.
 * - 본인 점수는 PointRepository.balance 를 그대로 사용.
 * - 본인을 'YOU' 로 표시하면서 정렬에 함께 포함.
 *
 * 실제 백엔드 연동은 본 MVP 범위 밖. 시연 의도를 잘 보여주는 데 집중.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<RankRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        val friends = loadMockFriends(ctx)
        val myScore = ServiceLocator.pointRepository.balance.first()
        val all = friends + RankRow(id = "me", name = "나", avatar = "🙂", score = myScore, isMe = true)
        rows = all.sortedByDescending { it.score }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ranking_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.ranking_caption),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows) { row ->
                    RankRowItem(row, rank = rows.indexOf(row) + 1)
                }
            }
        }
    }
}

@Composable
private fun RankRowItem(row: RankRow, rank: Int) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isMe) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = "#$rank",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = if (row.isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(40.dp),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (row.isMe) Color.White else BrandPurple),
                contentAlignment = Alignment.Center,
            ) { Text(row.avatar, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Text(
                row.name,
                fontWeight = FontWeight.Bold,
                color = if (row.isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${row.score}p",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = if (row.isMe) Color.White else BrandPurple,
            )
        }
    }
}

// ── mock 친구 로딩 ─────────────────────────────────────────────────

@Serializable
private data class FriendDto(
    val id: String,
    val name: String,
    val score: Int,
    val avatar: String,
)

@Serializable
private data class FriendsFile(
    @SerialName("friends") val friends: List<FriendDto>,
)

data class RankRow(
    val id: String,
    val name: String,
    val avatar: String,
    val score: Int,
    val isMe: Boolean = false,
)

private fun loadMockFriends(ctx: android.content.Context): List<RankRow> {
    val raw = ctx.assets.open("mock_friends.json").bufferedReader().use { it.readText() }
    val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(FriendsFile.serializer(), raw)
    return parsed.friends.map {
        RankRow(id = it.id, name = it.name, avatar = it.avatar, score = it.score)
    }
}
