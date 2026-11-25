package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val dob: String = "",
    val email: String = "",
    val address: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SampleCustomerSeed(
    val name: String,
    val phone: String,
    val dob: String,
    val email: String,
    val address: String
)

object SampleCustomers {
    val defaultList: List<SampleCustomerSeed> = listOf(
        SampleCustomerSeed(
            name = "Aarav Sharma",
            phone = "9876543210",
            dob = "14/08/1995",
            email = "aarav.sharma@example.in",
            address = "42 MG Road, Indiranagar, Bengaluru, Karnataka 560038"
        ),
        SampleCustomerSeed(
            name = "Priya Patel",
            phone = "9820456712",
            dob = "22/03/1998",
            email = "priya.patel@example.in",
            address = "15 SG Highway, Satellite, Ahmedabad, Gujarat 380015"
        ),
        SampleCustomerSeed(
            name = "Rohan Deshmukh",
            phone = "9765123489",
            dob = "09/11/1992",
            email = "rohan.deshmukh@example.in",
            address = "204 FC Road, Shivajinagar, Pune, Maharashtra 411004"
        ),
        SampleCustomerSeed(
            name = "Ananya Iyer",
            phone = "9840987654",
            dob = "30/06/1996",
            email = "ananya.iyer@example.in",
            address = "88 Anna Salai, Teynampet, Chennai, Tamil Nadu 600018"
        ),
        SampleCustomerSeed(
            name = "Vikram Malhotra",
            phone = "9811234567",
            dob = "17/01/1990",
            email = "vikram.malhotra@example.in",
            address = "12 Connaught Place, New Delhi, Delhi 110001"
        )
    )
}
