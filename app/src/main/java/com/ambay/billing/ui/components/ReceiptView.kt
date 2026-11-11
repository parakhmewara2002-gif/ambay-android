package com.ambay.billing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambay.billing.core.model.Money
import com.ambay.billing.core.model.Sale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptView(
    sale: Sale,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
    val dateStr = dateFormat.format(Date(sale.date))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AMBAY MOBILE ACCESSORIES",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Mobile & Accessories Specialist",
                fontSize = 11.sp,
                color = Color.DarkGray
            )
            Text(
                text = "Without Bill / Cash Memo",
                fontSize = 11.sp,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sale: ${sale.no}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Mode: ${sale.pay}",
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
            Text(
                text = "Date: $dateStr",
                fontSize = 11.sp,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth()
            )
            if (sale.cust.name.isNotEmpty()) {
                Text(
                    text = "Customer: ${sale.cust.name}",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(6.dp))

            // Lines
            for (line in sale.lines) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = line.name + (if (line.model.isNotEmpty()) " (${line.model})" else "") + (if (line.ws == "W") " [WAR]" else ""),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Text(
                            text = "${line.qty} x ${Money.formatInr(line.op)}" + (if (line.disc > 0) " (-${Money.formatInr(line.disc)})" else ""),
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        text = Money.formatInr(line.total),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Total Qty: ${sale.tot.qty}",
                    fontSize = 12.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                if (sale.tot.disc > 0) {
                    Text(
                        text = "Disc: -${Money.formatInr(sale.tot.disc)}",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NET TOTAL:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Money.formatInr(sale.tot.total),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B3D91)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Dhanyavaad! Phir Padhaarein.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = "Txn ID: ${sale.id.take(8)}...",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun DashedDivider() {
    Text(
        text = "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
        fontSize = 10.sp,
        color = Color(0xFFBDBDBD),
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
    )
}
