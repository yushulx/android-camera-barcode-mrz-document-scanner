package com.google.android.gms.samples.vision.barcodereader;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import com.google.android.gms.vision.barcode.Barcode;

import java.sql.Driver;

public class ResultActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setVerticalScrollBarEnabled(true);
        tv.setText("");
        tv.setMovementMethod(new ScrollingMovementMethod());

        Intent intent = getIntent();
        if (intent != null) {
            Barcode.DriverLicense driverLicense = (Barcode.DriverLicense) intent.getParcelableExtra("DriverLicense");
            if (driverLicense != null) {
                String documentType = driverLicense.documentType;
                tv.append("Document Type:\n" + documentType + "\n\n");
                String firstName = driverLicense.firstName;
                tv.append("First Name:\n" + firstName + "\n\n");
                String middleName = driverLicense.middleName;
                tv.append("Middle Name:\n" + middleName + "\n\n");
                String lastName = driverLicense.lastName;
                tv.append("Last Name:\n" + lastName + "\n\n");
                String gender = driverLicense.gender;
                tv.append("Gender: \n" + gender + "\n\n");
                String addressStreet = driverLicense.addressStreet;
                tv.append("Street:\n" + addressStreet + "\n\n");
                String addressCity = driverLicense.addressCity;
                tv.append("City:\n" + addressCity + "\n\n");
                String addressState = driverLicense.addressState;
                tv.append("State:\n" + addressState + "\n\n");
                String addressZip = driverLicense.addressZip;
                tv.append("Zip:\n" + addressZip + "\n\n");
                String licenseNumber = driverLicense.licenseNumber;
                tv.append("License Number:\n" + licenseNumber + "\n\n");
                String issueDate = driverLicense.issueDate;
                tv.append("Issue Date:\n" + issueDate + "\n\n");
                String expiryDate = driverLicense.expiryDate;
                tv.append("Expiry Date:\n" + expiryDate + "\n\n");
                String birthDate = driverLicense.birthDate;
                tv.append("Birth Date:\n" + birthDate + "\n\n");
                String issuingCountry = driverLicense.issuingCountry;
                tv.append("Issue Country:\n" + issuingCountry + "\n\n");
            }
        }

        setContentView(tv);
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
