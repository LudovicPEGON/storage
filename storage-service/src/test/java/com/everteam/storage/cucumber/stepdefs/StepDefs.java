package com.everteam.storage.cucumber.stepdefs;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.ResultActions;

import com.everteam.storage.StorageApp;

import org.springframework.boot.test.context.SpringBootTest;

@WebAppConfiguration
@SpringBootTest
@ContextConfiguration(classes = StorageApp.class)
public abstract class StepDefs {

    protected ResultActions actions;

}
