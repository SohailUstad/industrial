package com.homerun.marketplace.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.UUID

private val Ink = Color(0xFF132E2A)
private val Forest = Color(0xFF0D6C5D)
private val Mint = Color(0xFFE7F5EC)
private val Canvas = Color(0xFFF6F8F5)
private val Muted = Color(0xFF6D7C75)
private val Amber = Color(0xFFFFD271)

private enum class Screen { HOME, CATEGORIES, CART, DETAIL, CHECKOUT, CONFIRMATION }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(19, 46, 42)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Forest, onPrimary = Color.White, background = Canvas,
                surface = Color.White, onSurface = Ink, secondary = Amber
            )) { MarketplaceApp() }
        }
    }
}

@Composable
private fun MarketplaceApp() {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val cart = remember { mutableStateMapOf<String, Int>() }
    var details by remember { mutableStateOf(CheckoutDetails()) }
    var checkoutError by remember { mutableStateOf<String?>(null) }
    var placing by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<OrderReceipt?>(null) }
    var orderKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true; catalogError = null
            try { products = MarketplaceApi.products() }
            catch (error: Exception) { catalogError = error.message ?: "Could not load the catalog" }
            finally { loading = false }
        }
    }
    fun changeQuantity(product: Product, change: Int) {
        val next = (cart[product.id] ?: 0) + change
        if (next <= 0) cart.remove(product.id) else cart[product.id] = next.coerceAtMost(999)
        orderKey = UUID.randomUUID().toString()
    }
    fun submitOrder() {
        if (details.name.isBlank() || details.phone.length < 7 || details.address.isBlank() ||
            details.city.isBlank() || details.postalCode.isBlank()) {
            checkoutError = "Complete your name, phone number and delivery address."
            return
        }
        val snapshot = products.mapNotNull { p -> cart[p.id]?.let { p to it } }
        if (snapshot.isEmpty()) return
        scope.launch {
            placing = true; checkoutError = null
            try {
                receipt = MarketplaceApi.placeOrder(details, snapshot, orderKey)
                cart.clear()
                screen = Screen.CONFIRMATION
            } catch (error: Exception) {
                if (error.message?.contains("Prices changed") == true) {
                    try {
                        products = MarketplaceApi.products()
                        orderKey = UUID.randomUUID().toString()
                        checkoutError = "Prices changed. Review the updated total, then place the order again."
                    } catch (_: Exception) {
                        checkoutError = "Prices changed. Return to the catalog and retry."
                    }
                } else checkoutError = error.message ?: "Could not place the order. Please retry."
            } finally { placing = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val cartItems = products.mapNotNull { p -> cart[p.id]?.let { p to it } }
    val cartCount = cartItems.sumOf { it.second }
    val subtotal = cartItems.sumOf { (product, quantity) -> product.priceMinor * quantity }
    val categories = products.map { it.category }.distinct().sorted()
    val showNavigation = screen in setOf(Screen.HOME, Screen.CATEGORIES, Screen.CART)

    BackHandler(screen != Screen.HOME) {
        screen = when (screen) {
            Screen.DETAIL -> Screen.HOME
            Screen.CHECKOUT -> Screen.CART
            else -> Screen.HOME
        }
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            when (screen) {
                Screen.HOME, Screen.CATEGORIES, Screen.CART -> Column {
                    if (cartCount > 0 && screen != Screen.CART) CartShortcut(cartCount, subtotal) { screen = Screen.CART }
                    if (screen == Screen.CART && cartCount > 0) BottomAction("Continue to checkout", money(subtotal)) { screen = Screen.CHECKOUT }
                    if (showNavigation) NavigationBar(containerColor = Color.White, tonalElevation = 5.dp) {
                        NavigationBarItem(screen == Screen.HOME, { screen = Screen.HOME }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                        NavigationBarItem(screen == Screen.CATEGORIES, { screen = Screen.CATEGORIES }, icon = { Icon(Icons.Default.List, null) }, label = { Text("Categories") })
                        NavigationBarItem(screen == Screen.CART, { screen = Screen.CART }, icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text(if (cartCount > 0) "Cart ($cartCount)" else "Cart") })
                    }
                }
                Screen.DETAIL -> selectedProduct?.let { product -> BottomAction("Add to cart", money(product.priceMinor)) { changeQuantity(product, 1); screen = Screen.CART } }
                Screen.CHECKOUT -> BottomAction(if (placing) "Placing order…" else "Place order · Cash on delivery", money(subtotal), !placing && cartCount > 0, ::submitOrder)
                Screen.CONFIRMATION -> BottomAction("Continue shopping", "") { screen = Screen.HOME }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(products, categories, loading, catalogError, search, category,
                    onSearch = { search = it }, onCategory = { category = it }, onRetry = ::refresh,
                    onProduct = { selectedProduct = it; screen = Screen.DETAIL },
                    quantity = { cart[it.id] ?: 0 }, onQuantity = ::changeQuantity)
                Screen.CATEGORIES -> CategoriesScreen(products, categories) { category = it; search = ""; screen = Screen.HOME }
                Screen.CART -> CartScreen(cartItems, subtotal, ::changeQuantity) { screen = Screen.HOME }
                Screen.DETAIL -> selectedProduct?.let { product -> ProductScreen(product) { screen = Screen.HOME } }
                Screen.CHECKOUT -> CheckoutScreen(cartItems, subtotal, details, checkoutError,
                    onDetails = { details = it }, onBack = { screen = Screen.CART })
                Screen.CONFIRMATION -> receipt?.let { ConfirmationScreen(it) { screen = Screen.HOME } }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    products: List<Product>, categories: List<String>, loading: Boolean, error: String?,
    search: String, category: String, onSearch: (String) -> Unit, onCategory: (String) -> Unit,
    onRetry: () -> Unit, onProduct: (Product) -> Unit,
    quantity: (Product) -> Int, onQuantity: (Product, Int) -> Unit
) {
    val filtered = products.filter { (category == "All" || it.category == category) &&
        (search.isBlank() || it.name.contains(search, true) || it.sku.contains(search, true) || it.category.contains(search, true)) }
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Amber, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("HOMERUN SUPPLY", color = Amber, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.3.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("Tools for every task", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text("Hardware, welding, paint and more", color = Color(0xFFBBD2C9), fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                TextField(search, onSearch, modifier = Modifier.fillMaxWidth().height(54.dp),
                    placeholder = { Text("Search nails, welding rods, paint…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Forest) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            }
        }
        if (search.isBlank() && category == "All") item {
            Row(Modifier.fillMaxWidth().padding(14.dp).background(Amber, RoundedCornerShape(18.dp)).padding(17.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BUILT FOR WORK", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Site essentials,\none easy shop.", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 23.sp)
                }
                Text("🧰", fontSize = 45.sp)
            }
        }
        item {
            Text("Shop by category", modifier = Modifier.padding(start = 16.dp, top = 13.dp, bottom = 9.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All") + categories) { name ->
                    FilterChip(selected = name == category, onClick = { onCategory(name) },
                        label = { Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = if (name == "All") null else ({ Text(categoryEmoji(name), fontSize = 15.sp) }))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (category == "All") "Popular picks" else category, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                if (!loading) Text("${filtered.size} items", color = Muted, fontSize = 12.sp)
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (error != null) item { MessageState(error, "Retry", onRetry) }
        else if (filtered.isEmpty()) item { MessageState("No products found", "Show all") { onSearch(""); onCategory("All") } }
        else items(filtered.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { product -> Box(Modifier.weight(1f)) { ProductTile(product, quantity(product), onProduct, onQuantity) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ProductTile(product: Product, quantity: Int, onProduct: (Product) -> Unit,
                        onQuantity: (Product, Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onProduct(product) },
        shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(10.dp)) {
            ProductVisual(product, Modifier.fillMaxWidth().height(112.dp))
            Spacer(Modifier.height(9.dp))
            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(product.unit.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money(product.priceMinor), Modifier.weight(1f), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                QuantityControl(quantity, compact = true, onAdd = { onQuantity(product, 1) }, onRemove = { onQuantity(product, -1) })
            }
        }
    }
}

@Composable
private fun ProductVisual(product: Product, modifier: Modifier = Modifier) {
    Box(modifier.background(categoryColor(product.category), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        if (product.imageUrl != null) AsyncImage(model = product.imageUrl, contentDescription = product.name,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(categoryEmoji(product.category), fontSize = 48.sp)
        Surface(modifier = Modifier.align(Alignment.TopStart).padding(7.dp), shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.9f)) {
            Text(product.category.uppercase(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Forest)
        }
    }
}

private fun categoryEmoji(category: String): String = when (category.lowercase()) {
    "fasteners" -> "🔩"; "welding" -> "⚡"; "paint" -> "🎨"; "tools" -> "🛠️"
    "safety" -> "🧤"; "adhesives" -> "🧴"; else -> "📦"
}
private fun categoryColor(category: String): Color = when (category.lowercase()) {
    "fasteners" -> Color(0xFFE8EFF4); "welding" -> Color(0xFFFBEBD8); "paint" -> Color(0xFFEDE8F8)
    "tools" -> Color(0xFFE6F1EA); "safety" -> Color(0xFFF5EEE0); else -> Color(0xFFEAF1EE)
}

@Composable
private fun QuantityControl(quantity: Int, compact: Boolean = false, onAdd: () -> Unit, onRemove: () -> Unit) {
    val height = if (compact) 32.dp else 40.dp
    if (quantity == 0) OutlinedButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 11.dp),
        modifier = Modifier.height(height), border = ButtonDefaults.outlinedButtonBorder,
        shape = RoundedCornerShape(9.dp)) { Text("ADD", fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 11.sp else 13.sp) }
    else Row(Modifier.height(height).background(Forest, RoundedCornerShape(9.dp)), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRemove, modifier = Modifier.size(height)) { Icon(Icons.Default.Remove, "Remove", tint = Color.White, modifier = Modifier.size(16.dp)) }
        Text("$quantity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        IconButton(onClick = onAdd, modifier = Modifier.size(height)) { Icon(Icons.Default.Add, "Add", tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun CategoriesScreen(products: List<Product>, categories: List<String>, onSelect: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item { Text("Categories", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp); Text("Find what your job needs", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(18.dp)) }
        items(categories.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { name ->
                    Card(Modifier.weight(1f).height(125.dp).clickable { onSelect(name) }, shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = categoryColor(name))) {
                        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(categoryEmoji(name), fontSize = 30.sp)
                            Column { Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text("${products.count { it.category == name }} products", color = Muted, fontSize = 11.sp) }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CartScreen(items: List<Pair<Product, Int>>, subtotal: Long,
                       onQuantity: (Product, Int) -> Unit, onShop: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item { Text("Your cart", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp); Text("Review items before checkout", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(18.dp)) }
        if (items.isEmpty()) item { MessageState("Your cart is empty", "Browse products", onShop) }
        else {
            items(items, key = { it.first.id }) { (product, count) ->
                Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductVisual(product, Modifier.size(70.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                            Text("${money(product.priceMinor)} / ${product.unit}", color = Muted, fontSize = 11.sp)
                            Text(money(product.priceMinor * count), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        QuantityControl(count, compact = true, onAdd = { onQuantity(product, 1) }, onRemove = { onQuantity(product, -1) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item {
                Spacer(Modifier.height(15.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Bill details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(10.dp))
                        SummaryLine("Items subtotal", money(subtotal))
                        SummaryLine("Delivery", "Free")
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SummaryLine("Pay on delivery", money(subtotal), bold = true)
                    }
                }
                Text("Vendor details are managed privately by Homerun Supply.", Modifier.padding(top = 15.dp), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProductScreen(product: Product, onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text("Products") }
            ProductVisual(product, Modifier.fillMaxWidth().height(245.dp).padding(horizontal = 16.dp))
            Column(Modifier.padding(20.dp)) {
                Text(product.category.uppercase(), color = Forest, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text(product.name, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 31.sp)
                Text("SKU ${product.sku}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Text("${money(product.priceMinor)} / ${product.unit}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(18.dp))
                Text("About this product", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(product.description, color = Muted, lineHeight = 21.sp, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CheckoutScreen(items: List<Pair<Product, Int>>, total: Long, details: CheckoutDetails,
                           error: String?, onDetails: (CheckoutDetails) -> Unit, onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text("Cart") }
            Text("Checkout", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Delivery details", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(17.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CheckoutField("Full name", details.name, { onDetails(details.copy(name = it)) }, capitalization = KeyboardCapitalization.Words)
                    CheckoutField("Phone number", details.phone, { onDetails(details.copy(phone = it)) }, keyboardType = KeyboardType.Phone)
                    CheckoutField("Street address and building", details.address, { onDetails(details.copy(address = it)) }, capitalization = KeyboardCapitalization.Words)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { CheckoutField("City", details.city, { onDetails(details.copy(city = it)) }, capitalization = KeyboardCapitalization.Words) }
                        Box(Modifier.weight(1f)) { CheckoutField("PIN / postal code", details.postalCode, { onDetails(details.copy(postalCode = it)) }, keyboardType = KeyboardType.Number) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Forest)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Cash on delivery", fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Pay when the order arrives", color = Muted, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(17.dp))
            Text("Order summary", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            items.forEach { (product, count) -> SummaryLine("${product.name} × $count", money(product.priceMinor * count)) }
            HorizontalDivider(Modifier.padding(vertical = 11.dp))
            SummaryLine("Items total", money(total), bold = true)
            Text("Availability and delivery are confirmed after order placement.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 15.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CheckoutField(label: String, value: String, onValue: (String) -> Unit,
                          keyboardType: KeyboardType = KeyboardType.Text,
                          capitalization: KeyboardCapitalization = KeyboardCapitalization.None) {
    OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) }, singleLine = true,
        shape = RoundedCornerShape(11.dp), textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization))
}

@Composable
private fun ConfirmationScreen(receipt: OrderReceipt, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CheckCircle, null, tint = Forest, modifier = Modifier.size(78.dp))
        Spacer(Modifier.height(20.dp))
        Text("Order placed!", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
        Text("We’ll confirm availability and delivery shortly.", color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                SummaryLine("Order ID", receipt.id.take(8).uppercase(), bold = true)
                SummaryLine("Status", receipt.status.lowercase().replaceFirstChar { it.uppercase() })
                SummaryLine("Payment", "Cash on delivery")
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SummaryLine("Order total", money(receipt.totalMinor), bold = true)
            }
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onHome) { Text("Back to shopping"); Icon(Icons.Default.ArrowForward, null) }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), color = if (bold) Ink else Muted,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Text(value, color = Ink, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 12.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun MessageState(message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📦", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, textAlign = TextAlign.Center)
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun CartShortcut(count: Int, total: Long, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = Forest) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text("$count items · ${money(total)}", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("View cart", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun BottomAction(label: String, amount: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp)) {
            Text(label, Modifier.weight(1f), textAlign = TextAlign.Start, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (amount.isNotBlank()) Text(amount, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.padding(start = 5.dp).size(17.dp))
        }
    }
}
