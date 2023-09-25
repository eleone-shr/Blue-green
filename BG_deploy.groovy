def req = ["env":"",
                   "changeRequest":""]
def env = '$(env)'
def buildID = '$(buildID)'
def Recipients = "examplename1@exampleemail.com, examplename2@exampleemail.com"

def Yamldata = [
    'apiVersion': 'v1',
    'environment': env,
    'version': 1.0,
    'appFamily': 'FBP',
    'appName': 'FBP-product-app',
    'buildID': buildID,
    'kind': 'deployment',
    'manifest': 'lines',
    'deployment': [ 
        'target': 'EKS',
        'style': 'non-intrusive',
        'window': 'asap'
    ],
    'email' : 'emails'
]

node {
    stage('Write Yaml') {
        writeYaml file: 'manifest.yaml', data: Yamldata, overwrite: true
        sh 'cat manifest.yaml'
    }

    stage('Env Select') {
        envTestList = ["DSIT","SITE","PTE","PROD","BLUE-GREEN","END"]
		
		req = ""
		
        req = input message: "Select an environment to deploy Artifact?",
            id: 'envResponse',
                parameters: [string(description: 'Change Request number from KISAM', name: 'changeRequest'),
                        choice(name: 'env',
                        choices: envTestList)]
        println(req)

        if (req.env == 'BLUE-GREEN') {
            echo 'Blue-green deployment selected'
            iepBlueGreen()
        }
    }
}

def iepBlueGreen() {
    response = input message "Fill out the following for a blue-green manifest.",
                     id: 'bgResponse',
                     parameters: [string(description: 'Current Version', name: 'currentVersion'),
                                  string(description: 'Updating Version', name: 'updatingVersion'),
                                  string(description: 'Current Build ID', name: 'currentBuildID'),
                                  string(description: 'Updating Build ID', name: 'updatingBuildID'),
                                  string(description: 'Blue Weight', name: 'blueWeight'),
                                  string(description: 'Green Weight', name: 'greenWeight'),
                                  choice(name: 'environment',
                                  choices: envTestList)]
    println(response)

}
                                  
